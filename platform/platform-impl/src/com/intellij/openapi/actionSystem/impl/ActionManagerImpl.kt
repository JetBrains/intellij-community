// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.openapi.actionSystem.impl

import com.intellij.AbstractBundle
import com.intellij.BundleBase
import com.intellij.DynamicBundle
import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.withClientId
import com.intellij.concurrency.installThreadContext
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.ide.DataManager
import com.intellij.ide.ProhibitAWTEvents
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.RawPluginDescriptor.ActionDescriptorAction
import com.intellij.ide.plugins.RawPluginDescriptor.ActionDescriptorGroup
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.idea.IdeaLogger
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionIdProvider
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl.Companion.onActionLoadedFromXml
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl.Companion.onAfterActionInvoked
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl.Companion.onBeforeActionInvoked
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.*
import com.intellij.openapi.actionSystem.ex.ActionUtil.showDumbModeWarning
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer.LightCustomizeStrategy
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.extensions.*
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.impl.ActionProcessor
import com.intellij.openapi.keymap.impl.KeymapImpl
import com.intellij.openapi.keymap.impl.UpdateResult
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.ProjectType
import com.intellij.openapi.util.*
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.awaitFocusSettlesDown
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.util.coroutines.childScope
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.serviceContainer.executeRegisterTaskForOldContent
import com.intellij.ui.ClientProperty
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.util.ArrayUtilRt
import com.intellij.util.DefaultBundleService
import com.intellij.util.SlowOperations
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.*
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.with
import com.intellij.util.containers.without
import com.intellij.util.ui.StartupUiUtil.addAwtListener
import com.intellij.util.ui.UIUtil
import com.intellij.util.xml.dom.XmlElement
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.awt.AWTEvent
import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import javax.swing.Icon
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val DEFAULT_ACTION_GROUP_CLASS_NAME = DefaultActionGroup::class.java.name

open class ActionManagerImpl protected constructor(private val coroutineScope: CoroutineScope) : ActionManagerEx() {
  private val notRegisteredInternalActionIds = ArrayList<String>()
  private val actionListeners = ContainerUtil.createLockFreeCopyOnWriteList<AnActionListener>()
  private val actionPopupMenuListeners = ContainerUtil.createLockFreeCopyOnWriteList<ActionPopupMenuListener>()
  private val popups = ArrayList<Any>()
  private var timer: MyTimer? = null
  private val actionPostInitRuntimeRegistrar: ActionRuntimeRegistrar

  override var lastPreformedActionId: String? = null

  override var prevPreformedActionId: String? = null

  private var lastTimeEditorWasTypedIn: Long = 0
  private var anonymousGroupIdCounter = 0

  private val actionPostInitRegistrar: PostInitActionRegistrar

  private val keymapToOperations: Map<String, List<KeymapShortcutOperation>>

  init {
    val app = ApplicationManager.getApplication()
    if (!app.isUnitTestMode && !app.isHeadlessEnvironment && !app.isCommandLine) {
      ThreadingAssertions.assertBackgroundThread()
    }

    val idToAction = HashMap<String, AnAction>(5_000, 0.5f)
    val boundShortcuts = HashMap<String, String>(512, 0.5f)
    val state = ActionManagerState()
    val actionPreInitRegistrar = ActionPreInitRegistrar(idToAction = idToAction, boundShortcuts = boundShortcuts, state = state)
    val keymapToOperations = HashMap<String, MutableList<KeymapShortcutOperation>>()
    doRegisterActions(modules = PluginManagerCore.getPluginSet().getEnabledModules(),
                      keymapToOperations = keymapToOperations,
                      actionRegistrar = actionPreInitRegistrar)

    coroutineScope.launch {
      val schema = CustomActionsSchema.getInstanceAsync()
      for (url in schema.getActions()) {
        schema.incrementModificationStamp()
      }
    }

    this.keymapToOperations = keymapToOperations

    val mutator = PreInitActionRuntimeRegistrar(idToAction = idToAction, actionRegistrar = actionPreInitRegistrar)

    val heavyTasks = mutableListOf<ActionConfigurationCustomizer.CustomizeStrategy>()
    ActionConfigurationCustomizer.EP.forEachExtensionSafe { extension ->
      val customizeStrategy = extension.customize()
      if (customizeStrategy is LightCustomizeStrategy) {
        // same thread - mutator is not thread-safe by intention
        // todo use plugin-aware coroutineScope
        coroutineScope.launch(Dispatchers.Unconfined) {
          customizeStrategy.customize(mutator)
        }
      }
      else {
        heavyTasks.add(customizeStrategy)
      }
    }

    // by intention, _after_ doRegisterActions
    actionPostInitRegistrar = PostInitActionRegistrar(idToAction = idToAction, boundShortcuts = boundShortcuts, state = state)
    actionPostInitRuntimeRegistrar = PostInitActionRuntimeRegistrar(actionPostInitRegistrar)

    for (customizeStrategy in heavyTasks) {
      when (customizeStrategy) {
        is ActionConfigurationCustomizer.SyncHeavyCustomizeStrategy -> {
          @Suppress("LeakingThis")
          customizeStrategy.customize(this)
        }
        is ActionConfigurationCustomizer.AsyncLightCustomizeStrategy -> {
          // execute after we set actionPostInitRegistrar
          coroutineScope.launch {
            customizeStrategy.customize(asActionRuntimeRegistrar())
          }
        }
        is LightCustomizeStrategy -> throw IllegalStateException("$customizeStrategy not expected")
      }
    }

    DYNAMIC_EP_NAME.forEachExtensionSafe { customizer ->
      callDynamicRegistration(customizer, mutator)
    }

    DYNAMIC_EP_NAME.addExtensionPointListener(coroutineScope, object : ExtensionPointListener<DynamicActionConfigurationCustomizer> {
      override fun extensionAdded(extension: DynamicActionConfigurationCustomizer, pluginDescriptor: PluginDescriptor) {
        callDynamicRegistration(extension, actionPostInitRuntimeRegistrar)
      }

      override fun extensionRemoved(extension: DynamicActionConfigurationCustomizer, pluginDescriptor: PluginDescriptor) {
        extension.unregisterActions(this@ActionManagerImpl)
      }
    })

    app.extensionArea.getExtensionPoint<Any>("com.intellij.editorActionHandler").addChangeListener(coroutineScope) {
      for (action in actionPostInitRegistrar.actions) {
        updateHandlers(action)
      }
    }
  }

  private fun callDynamicRegistration(customizer: DynamicActionConfigurationCustomizer, mutator: ActionRuntimeRegistrar) {
    if (customizer is LightCustomizeStrategy) {
      coroutineScope.launch(Dispatchers.Unconfined) {
        customizer.customize(mutator)
      }
    }
    else {
      customizer.registerActions(this)
    }
  }

  override fun getBoundActions(): Set<String> = actionPostInitRegistrar.getBoundActions()

  override fun getActionBinding(actionId: String): String? = actionPostInitRegistrar.getActionBinding(actionId)

  override fun bindShortcuts(sourceActionId: String, targetActionId: String) {
    actionPostInitRegistrar.bindShortcuts(sourceActionId = sourceActionId, targetActionId = targetActionId)
  }

  override fun unbindShortcuts(targetActionId: String) {
    actionPostInitRegistrar.unbindShortcuts(targetActionId)
  }

  final override fun asActionRuntimeRegistrar(): ActionRuntimeRegistrar = actionPostInitRuntimeRegistrar

  // for dynamic plugins
  internal fun registerActions(modules: Iterable<IdeaPluginDescriptorImpl>) {
    val keymapToOperations = HashMap<String, MutableList<KeymapShortcutOperation>>()
    doRegisterActions(modules = modules, keymapToOperations = keymapToOperations, actionRegistrar = actionPostInitRegistrar)
    if (keymapToOperations.isNotEmpty()) {
      val keymapManager = service<KeymapManager>()
      for ((keymapName, operations) in keymapToOperations) {
        val keymap = keymapManager.getKeymap(keymapName) as KeymapImpl? ?: continue
        keymap.initShortcuts(operations = operations, actionBinding = actionPostInitRegistrar::getActionBinding)
      }
    }
  }

  private fun doRegisterActions(modules: Iterable<IdeaPluginDescriptorImpl>,
                                keymapToOperations: MutableMap<String, MutableList<KeymapShortcutOperation>>,
                                actionRegistrar: ActionRegistrar) {
    for (module in modules) {
      registerPluginActions(module = module, keymapToOperations = keymapToOperations, actionRegistrar = actionRegistrar)
      executeRegisterTaskForOldContent(module) {
        registerPluginActions(module = it, keymapToOperations = keymapToOperations, actionRegistrar = actionRegistrar)
      }
    }
  }

  internal fun getKeymapPendingOperations(keymapName: String): List<KeymapShortcutOperation> {
    return keymapToOperations.get(keymapName) ?: java.util.List.of()
  }

  final override fun addTimerListener(listener: TimerListener) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    if (timer == null) {
      timer = MyTimer(coroutineScope.childScope())
    }

    val wrappedListener = if (AppExecutorUtil.propagateContext() && listener !is CapturingListener) {
      CapturingListener(listener)
    }
    else {
      listener
    }
    timer!!.listeners.add(wrappedListener)
  }

  @Experimental
  @Internal
  fun reinitializeTimer() {
    val timer = timer ?: return
    val oldListeners = timer.listeners
    timer.stop()
    this.timer = null
    for (listener in oldListeners) {
      addTimerListener(listener)
    }
  }

  final override fun removeTimerListener(listener: TimerListener) {
    if (listener is CapturingListener) {
      listener.childContext.continuation?.context?.job?.cancel()
    }

    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    if (LOG.assertTrue(timer != null)) {
      timer!!.listeners.removeIf {
        it == listener || (it is CapturingListener && it.timerListener == listener)
      }
    }
  }

  open fun createActionPopupMenu(place: String, group: ActionGroup, presentationFactory: PresentationFactory?): ActionPopupMenu {
    return ActionPopupMenuImpl(place, group, this, presentationFactory)
  }

  override fun createActionPopupMenu(place: String, group: ActionGroup): ActionPopupMenu {
    return ActionPopupMenuImpl(place, group, this, null)
  }

  override fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean): ActionToolbar {
    return createActionToolbar(place = place, group = group, horizontal = horizontal, decorateButtons = false, customizable = true)
  }

  override fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean, decorateButtons: Boolean): ActionToolbar {
    return createActionToolbarImpl(place = place,
                                   group = group,
                                   horizontal = horizontal,
                                   decorateButtons = decorateButtons,
                                   customizable = false)
  }

  override fun createActionToolbar(place: String,
                                   group: ActionGroup,
                                   horizontal: Boolean,
                                   decorateButtons: Boolean,
                                   customizable: Boolean): ActionToolbar {
    return createActionToolbarImpl(place = place,
                                   group = group,
                                   horizontal = horizontal,
                                   decorateButtons = decorateButtons,
                                   customizable = customizable)
  }

  final override fun createActionToolbar(place: String,
                                         group: ActionGroup,
                                         horizontal: Boolean,
                                         separatorCreator: Function<in String, out Component>): ActionToolbar {
    val toolbar = createActionToolbarImpl(place = place,
                                          group = group,
                                          horizontal = horizontal,
                                          decorateButtons = false,
                                          customizable = true)
    toolbar.setSeparatorCreator(separatorCreator)
    return toolbar
  }

  private fun registerPluginActions(module: IdeaPluginDescriptorImpl,
                                    keymapToOperations: MutableMap<String, MutableList<KeymapShortcutOperation>>,
                                    actionRegistrar: ActionRegistrar) {
    val elements = module.actions
    if (elements.isEmpty()) {
      return
    }

    val startTime = System.nanoTime()
    var lastBundleName: String? = null
    var lastBundle: ResourceBundle? = null
    for (descriptor in elements) {
      val bundleName = descriptor.resourceBundle
                       ?: if (PluginManagerCore.CORE_ID == module.pluginId) "messages.ActionsBundle" else module.resourceBundleBaseName
      val element = descriptor.element
      var bundle: ResourceBundle?
      when (bundleName) {
        null -> bundle = null
        lastBundleName -> bundle = lastBundle
        else -> {
          try {
            bundle = DynamicBundle.getResourceBundle(module.classLoader, bundleName)
            lastBundle = bundle
            lastBundleName = bundleName
          }
          catch (e: MissingResourceException) {
            LOG.error(PluginException("Cannot resolve resource bundle $bundleName for action $element", e, module.pluginId))
            bundle = null
          }
        }
      }

      val bundleSupplier = { bundleName?.let { DynamicBundle.getResourceBundle(module.classLoader, it) } ?: bundle }

      when (descriptor) {
        is ActionDescriptorAction -> {
          processActionElement(className = descriptor.className,
                               isInternal = descriptor.isInternal,
                               element = element,
                               actionRegistrar = actionRegistrar,
                               module = module,
                               bundleSupplier = bundleSupplier,
                               keymapToOperations = keymapToOperations,
                               classLoader = module.classLoader)
        }
        is ActionDescriptorGroup -> {
          processGroupElement(className = descriptor.className,
                              id = descriptor.id,
                              element = element,
                              actionRegistrar = actionRegistrar,
                              module = module,
                              bundleSupplier = bundleSupplier,
                              keymapToOperations = keymapToOperations,
                              classLoader = module.classLoader)
        }
        else -> {
          when (descriptor.name) {
            ActionDescriptorName.separator -> processSeparatorNode(parentGroup = null,
                                                                   element = element,
                                                                   module = module,
                                                                   bundleSupplier = bundleSupplier,
                                                                   actionRegistrar = actionRegistrar)
            ActionDescriptorName.reference -> processReferenceNode(element = element,
                                                                   module = module,
                                                                   bundleSupplier = bundleSupplier,
                                                                   actionRegistrar = actionRegistrar)
            ActionDescriptorName.unregister -> processUnregisterNode(element = element, module = module, actionRegistrar = actionRegistrar)
            ActionDescriptorName.prohibit -> processProhibitNode(element = element, module = module)
            else -> LOG.error("${descriptor.name} is unknown")
          }
        }
      }
    }
    StartUpMeasurer.addPluginCost(module.pluginId.idString, "Actions", System.nanoTime() - startTime)
  }

  final override fun getAction(id: String): AnAction? {
    val action = getAction(id = id, canReturnStub = false, actionRegistrar = actionPostInitRegistrar)
    if (action == null && SystemProperties.getBooleanProperty("action.manager.log.available.actions.if.not.found", false)) {
      val availableActionIds = actionPostInitRegistrar.getActionIdList("")
      LOG.info("Action $id is not found. Available actions: $availableActionIds")
    }
    return action
  }

  override fun getId(action: AnAction): String? = actionPostInitRegistrar.getId(action)

  final override fun getActionIdList(idPrefix: String): List<String> {
    return actionPostInitRegistrar.getActionIdList(idPrefix)
  }

  @Suppress("OVERRIDE_DEPRECATION")
  final override fun getActionIds(idPrefix: String): Array<String> = ArrayUtilRt.toStringArray(getActionIdList(idPrefix))

  final override fun isGroup(actionId: String): Boolean {
    return getAction(id = actionId, canReturnStub = true, actionPostInitRegistrar) is ActionGroup
  }

  final override fun getActionOrStub(id: String): AnAction? {
    return getAction(id = id, canReturnStub = true, actionRegistrar = actionPostInitRegistrar)
  }

  @Experimental
  @Internal
  fun actionsOrStubs(): Sequence<AnAction> = actionPostInitRegistrar.actionsOrStubs()

  @Experimental
  @Internal
  fun unstubbedActions(filter: (String) -> Boolean): Sequence<AnAction> = actionPostInitRegistrar.unstubbedActions(filter)

  @Experimental
  @Internal
  fun groupIds(actionId: String): List<String> = actionPostInitRegistrar.groupIds(actionId)

  /**
   * @return instance of ActionGroup or ActionStub. The method never returns real subclasses of `AnAction`.
   */
  private fun processActionElement(className: String,
                                   isInternal: Boolean,
                                   element: XmlElement,
                                   actionRegistrar: ActionRegistrar,
                                   module: IdeaPluginDescriptorImpl,
                                   bundleSupplier: () -> ResourceBundle?,
                                   keymapToOperations: MutableMap<String, MutableList<KeymapShortcutOperation>>,
                                   classLoader: ClassLoader): AnAction? {
    // read ID and register a loaded action
    val id = obtainActionId(element = element, className = className)
    if (actionRegistrar.state.prohibitedActionIds.contains(id)) {
      return null
    }

    if (isInternal && !ApplicationManager.getApplication().isInternal) {
      notRegisteredInternalActionIds.add(id)
      return null
    }

    val iconPath = element.attributes.get(ICON_ATTR_NAME)
    val projectType = element.attributes.get(PROJECT_TYPE)
    val textValue = element.attributes.get(TEXT_ATTR_NAME)

    @Suppress("HardCodedStringLiteral")
    val descriptionValue = element.attributes.get(DESCRIPTION)
    val stub = ActionStub(className, id, module, iconPath, ProjectType.create(projectType)) {
      val presentation = Presentation.newTemplatePresentation()
      presentation.setText {
        computeActionText(bundleSupplier = bundleSupplier, id = id, elementType = ACTION_ELEMENT_NAME, textValue = textValue, classLoader = classLoader)
      }
      if (bundleSupplier() == null) {
        presentation.description = descriptionValue
      }
      else {
        presentation.setDescription {
          computeDescription(bundleSupplier = bundleSupplier,
                             id = id,
                             elementType = ACTION_ELEMENT_NAME,
                             descriptionValue = descriptionValue,
                             classLoader = classLoader)
        }
      }
      presentation
    }

    // process all links and key bindings if any
    for (child in element.children) {
      when (child.name) {
        ADD_TO_GROUP_ELEMENT_NAME -> processAddToGroupNode(action = stub,
                                                           element = child,
                                                           module = module,
                                                           secondary = isSecondary(child),
                                                           actionRegistrar = actionRegistrar)
        "keyboard-shortcut" -> processKeyboardShortcutNode(element = child,
                                                           actionId = id,
                                                           module = module,
                                                           keymapToOperations = keymapToOperations)
        "mouse-shortcut" -> processMouseShortcutNode(element = child,
                                                     actionId = id,
                                                     module = module,
                                                     keymapToOperations = keymapToOperations)
        "abbreviation" -> processAbbreviationNode(e = child, id = id)
        OVERRIDE_TEXT_ELEMENT_NAME -> processOverrideTextNode(action = stub,
                                                              id = stub.id,
                                                              element = child,
                                                              module = module,
                                                              bundleSupplier = bundleSupplier)
        SYNONYM_ELEMENT_NAME -> processSynonymNode(action = stub, element = child, module = module, bundleSupplier = bundleSupplier)
        else -> {
          reportActionError(module, "unexpected name of element \"${child.name}\"")
          return null
        }
      }
    }

    element.attributes.get(USE_SHORTCUT_OF_ATTR_NAME)?.let {
      actionRegistrar.bindShortcuts(sourceActionId = it, targetActionId = id)
    }
    registerOrReplaceActionInner(element = element, id = id, action = stub, plugin = module, actionRegistrar = actionRegistrar)
    return stub
  }

  private fun processGroupElement(className: String?,
                                  id: String?,
                                  element: XmlElement,
                                  module: IdeaPluginDescriptorImpl,
                                  bundleSupplier: () -> ResourceBundle?,
                                  keymapToOperations: MutableMap<String, MutableList<KeymapShortcutOperation>>,
                                  actionRegistrar: ActionRegistrar,
                                  classLoader: ClassLoader): AnAction? {
    try {
      if (id != null && actionRegistrar.state.prohibitedActionIds.contains(id)) {
        return null
      }

      // icon
      val iconPath = element.attributes.get(ICON_ATTR_NAME)

      val group: ActionGroup
      var customClass = false
      if (className == null || className == DEFAULT_ACTION_GROUP_CLASS_NAME) {
        if (id == null || iconPath == null) {
          group = DefaultActionGroup()
        }
        else {
          group = ActionGroupStub(id = id, actionClass = DEFAULT_ACTION_GROUP_CLASS_NAME, plugin = module, iconPath = iconPath)
        }
      }
      else if (className == DefaultCompactActionGroup::class.java.name) {
        group = DefaultCompactActionGroup()
      }
      else if (id == null) {
        val obj = ApplicationManager.getApplication().instantiateClass<Any>(className, module)
        if (obj !is ActionGroup) {
          reportActionError(module, "class with name \"$className\" should be instance of ${ActionGroup::class.java.name}")
          return null
        }

        if (element.children.size != element.count(ADD_TO_GROUP_ELEMENT_NAME)) {
          if (obj !is DefaultActionGroup) {
            reportActionError(module, "class with name \"$className\" should be instance of $DEFAULT_ACTION_GROUP_CLASS_NAME" +
                                      " because there are children specified")
            return null
          }
        }
        customClass = true
        group = obj
      }
      else {
        group = ActionGroupStub(id = id, actionClass = className, plugin = module, iconPath = iconPath)
        customClass = true
      }

      // read ID and register loaded group
      if (element.attributes.get(INTERNAL_ATTR_NAME).toBoolean() && !ApplicationManager.getApplication().isInternal) {
        notRegisteredInternalActionIds.add(id!!)
        return null
      }

      @Suppress("NAME_SHADOWING")
      val id = id ?: "<anonymous-group-${anonymousGroupIdCounter++}>"
      val popup = element.attributes.get("popup")
      if (popup != null) {
        group.isPopup = popup.toBoolean()
        if (group is ActionGroupStub) {
          group.popupDefinedInXml = true
        }
      }

      registerOrReplaceActionInner(element = element, id = id, action = group, plugin = module, actionRegistrar = actionRegistrar)

      configureGroupDescriptionAndIcon(presentation = group.templatePresentation,
                                       description = element.attributes.get(DESCRIPTION),
                                       textValue = element.attributes.get(TEXT_ATTR_NAME),
                                       group = group,
                                       bundleSupplier = bundleSupplier,
                                       id = id,
                                       classLoader = classLoader,
                                       iconPath = iconPath,
                                       module = module,
                                       className = className)

      val searchable = element.attributes.get("searchable")
      if (searchable != null) {
        group.isSearchable = searchable.toBoolean()
      }
      val shortcutOfActionId = element.attributes.get(USE_SHORTCUT_OF_ATTR_NAME)
      if (customClass && shortcutOfActionId != null) {
        actionRegistrar.bindShortcuts(sourceActionId = shortcutOfActionId, targetActionId = id)
      }

      // Process all group's children. There are other groups, actions, references and links
      for (child in element.children) {
        when (child.name) {
          ACTION_ELEMENT_NAME -> {
            val childClassName = child.attributes.get(CLASS_ATTR_NAME)
            if (childClassName.isNullOrEmpty()) {
              reportActionError(module = module, message = "action element should have specified \"class\" attribute")
            }
            else {
              val action = processActionElement(className = childClassName,
                                                isInternal = child.attributes.get(INTERNAL_ATTR_NAME).toBoolean(),
                                                element = child,
                                                module = module,
                                                bundleSupplier = bundleSupplier,
                                                actionRegistrar = actionRegistrar,
                                                keymapToOperations = keymapToOperations,
                                                classLoader = classLoader)
              if (action != null) {
                addToGroup(group = group,
                           action = action,
                           constraints = Constraints.LAST,
                           module = module,
                           state = actionRegistrar.state,
                           secondary = isSecondary(child))
              }
            }
          }
          SEPARATOR_ELEMENT_NAME -> {
            processSeparatorNode(parentGroup = group as DefaultActionGroup,
                                 element = child,
                                 module = module,
                                 bundleSupplier = bundleSupplier,
                                 actionRegistrar = actionRegistrar)
          }
          GROUP_ELEMENT_NAME -> {
            var childClassName = child.attributes.get(CLASS_ATTR_NAME)
            if (childClassName == null) {
              // use a default group if class isn't specified
              childClassName = if ("true" == child.attributes.get("compact")) {
                DefaultCompactActionGroup::class.java.name
              }
              else {
                DEFAULT_ACTION_GROUP_CLASS_NAME
              }
            }
            val childId = child.attributes.get(ID_ATTR_NAME)
            if (childId != null && childId.isEmpty()) {
              reportActionError(module, "ID of the group cannot be an empty string")
            }
            else {
              val action = processGroupElement(className = childClassName,
                                               id = childId,
                                               element = child,
                                               module = module,
                                               bundleSupplier = bundleSupplier,
                                               keymapToOperations = keymapToOperations,
                                               actionRegistrar = actionRegistrar,
                                               classLoader = classLoader)
              if (action != null) {
                addToGroup(group = group,
                           action = action,
                           constraints = Constraints.LAST,
                           module = module,
                           state = actionRegistrar.state,
                           secondary = false)
              }
            }
          }
          ADD_TO_GROUP_ELEMENT_NAME -> {
            processAddToGroupNode(action = group,
                                  element = child,
                                  module = module,
                                  secondary = isSecondary(child),
                                  actionRegistrar = actionRegistrar)
          }
          REFERENCE_ELEMENT_NAME -> {
            val action = processReferenceElement(element = child, module = module, actionRegistrar = actionRegistrar)
            if (action != null) {
              addToGroup(group = group,
                         action = action,
                         constraints = Constraints.LAST,
                         module = module,
                         state = actionRegistrar.state,
                         secondary = isSecondary(child))
            }
          }
          OVERRIDE_TEXT_ELEMENT_NAME -> processOverrideTextNode(action = group, id = id, element = child, module = module, bundleSupplier = bundleSupplier)
          else -> {
            reportActionError(module, "unexpected name of element \"${child.name}\n")
            return null
          }
        }
      }
      return group
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      reportActionError(module = module, message = "cannot create class \"$className\"", cause = e)
      return null
    }
  }

  private fun processReferenceNode(element: XmlElement,
                                   module: IdeaPluginDescriptor,
                                   bundleSupplier: () -> ResourceBundle?,
                                   actionRegistrar: ActionRegistrar) {
    val action = processReferenceElement(element = element, module = module, actionRegistrar = actionRegistrar) ?: return
    for (child in element.children) {
      if (ADD_TO_GROUP_ELEMENT_NAME == child.name) {
        processAddToGroupNode(action = action,
                              element = child,
                              module = module,
                              secondary = isSecondary(child),
                              actionRegistrar = actionRegistrar)
      }
      else if (SYNONYM_ELEMENT_NAME == child.name) {
        processSynonymNode(action = action, element = child, module = module, bundleSupplier = bundleSupplier)
      }
    }
  }

  /**
   * @param element description of a link
   */
  private fun processAddToGroupNode(action: AnAction,
                                    element: XmlElement,
                                    module: IdeaPluginDescriptor,
                                    secondary: Boolean,
                                    actionRegistrar: ActionRegistrar) {
    val name = if (action is ActionStub) action.className else action.javaClass.name
    val id = if (action is ActionStub) action.id else actionRegistrar.state.actionToId.get(action)!!
    val actionName = "$name ($id)"

    // parent group
    val parentGroup = getParentGroup(groupId = element.attributes.get(GROUP_ID_ATTR_NAME),
                                     actionName = actionName,
                                     module = module,
                                     actionRegistrar = actionRegistrar) ?: return

    // anchor attribute
    val anchor = parseAnchor(element.attributes.get("anchor"), actionName, module) ?: return
    val relativeToActionId = element.attributes.get("relative-to-action")
    if ((Anchor.BEFORE == anchor || Anchor.AFTER == anchor) && relativeToActionId == null) {
      reportActionError(module, "$actionName: \"relative-to-action\" cannot be null if anchor is \"after\" or \"before\"")
      return
    }

    addToGroup(group = parentGroup,
               action = action,
               state = actionRegistrar.state,
               constraints = Constraints(anchor, relativeToActionId),
               module = module,
               secondary = secondary)
  }

  fun addToGroup(group: DefaultActionGroup, action: AnAction, constraints: Constraints) {
    addToGroup(group = group,
               action = action,
               constraints = constraints,
               module = null,
               secondary = false,
               state = actionPostInitRegistrar.state)
  }

  private fun getParentGroup(groupId: String?,
                             actionName: String?,
                             module: IdeaPluginDescriptor,
                             actionRegistrar: ActionRegistrar): DefaultActionGroup? {
    if (groupId.isNullOrEmpty()) {
      reportActionError(module, "$actionName: attribute \"group-id\" should be defined")
      return null
    }

    val parentGroup = getAction(id = groupId, canReturnStub = true, actionRegistrar = actionRegistrar)
    if (parentGroup == null) {
      reportActionError(module = module,
                        message = "$actionName: group with id \"$groupId\" isn't registered so the action won't be added to it; the action can be invoked via \"Find Action\"",
                        cause = null)
      return null
    }
    if (parentGroup !is DefaultActionGroup) {
      reportActionError(module, "$actionName: group with id \"$groupId\" should be instance of ${DefaultActionGroup::class.java.name}" +
                                " but was ${parentGroup.javaClass}")
      return null
    }
    return parentGroup
  }

  /**
   * @param parentGroup group which is the parent of the separator. It can be `null` in that
   * case separator will be added to a group described in the <add-to-group ...> sub element.
   * @param element     XML element which represent separator. `</add-to-group>`
   */
  @Suppress("HardCodedStringLiteral")
  private fun processSeparatorNode(parentGroup: DefaultActionGroup?,
                                   element: XmlElement,
                                   module: IdeaPluginDescriptor,
                                   bundleSupplier: () -> ResourceBundle?,
                                   actionRegistrar: ActionRegistrar) {
    val text = element.attributes.get(TEXT_ATTR_NAME)
    val key = element.attributes.get(KEY_ATTR_NAME)
    val separator = when {
      text != null -> Separator(text)
      key != null -> createSeparator(bundleSupplier, key)
      else -> Separator.getInstance()
    }
    parentGroup?.add(separator, this)
    // try to find inner <add-to-parent...> tag
    for (child in element.children) {
      if (ADD_TO_GROUP_ELEMENT_NAME == child.name) {
        processAddToGroupNode(action = separator,
                              element = child,
                              module = module,
                              secondary = isSecondary(child),
                              actionRegistrar = actionRegistrar)
      }
    }
  }

  private fun processProhibitNode(element: XmlElement, module: IdeaPluginDescriptor) {
    val id = element.attributes.get(ID_ATTR_NAME)
    if (id == null) {
      reportActionError(module, "'id' attribute is required for 'unregister' elements")
      return
    }
    prohibitAction(id)
  }

  private fun processUnregisterNode(element: XmlElement, module: IdeaPluginDescriptor, actionRegistrar: ActionRegistrar) {
    val id = element.attributes.get(ID_ATTR_NAME)
    if (id == null) {
      reportActionError(module, "'id' attribute is required for 'unregister' elements")
      return
    }

    val action = getAction(id = id, canReturnStub = false, actionRegistrar = actionRegistrar)
    if (action == null) {
      reportActionError(module, "Trying to unregister non-existing action $id")
      return
    }

    AbbreviationManager.getInstance().removeAllAbbreviations(id)
    unregisterAction(actionId = id, actionRegistrar = actionRegistrar)
  }

  private fun processReferenceElement(element: XmlElement, module: IdeaPluginDescriptor, actionRegistrar: ActionRegistrar): AnAction? {
    val ref = getReferenceActionId(element)
    if (ref.isNullOrEmpty()) {
      reportActionError(module, "ID of reference element should be defined", null)
      return null
    }

    if (actionRegistrar.state.prohibitedActionIds.contains(ref)) {
      return null
    }

    val action = getAction(id = ref, canReturnStub = true, actionRegistrar = actionRegistrar)
    if (action == null) {
      if (!notRegisteredInternalActionIds.contains(ref)) {
        reportActionError(module, "action specified by reference isn't registered (ID=$ref)", null)
      }
      return null
    }
    return action
  }

  fun unloadActions(module: IdeaPluginDescriptorImpl) {
    val descriptors = module.actions
    for (i in descriptors.indices.reversed()) {
      val descriptor = descriptors[i]
      val element = descriptor.element
      when (descriptor.name) {
        ActionDescriptorName.action -> unloadActionElement(element)
        ActionDescriptorName.group -> unloadGroupElement(element)
        ActionDescriptorName.reference -> {
          val action = processReferenceElement(element = element, module = module, actionRegistrar = actionPostInitRegistrar) ?: return
          val actionId = getReferenceActionId(element)
          for ((name, attributes) in element.children) {
            if (name != ADD_TO_GROUP_ELEMENT_NAME) {
              continue
            }

            val groupId = attributes.get(GROUP_ID_ATTR_NAME)
            val parentGroup = getParentGroup(groupId = groupId,
                                             actionName = actionId,
                                             module = module,
                                             actionRegistrar = actionPostInitRegistrar) ?: return
            parentGroup.remove(action)
            if (groupId != null && actionId != null) {
              actionPostInitRegistrar.state.removeGroupMapping(actionId, groupId)
            }
          }
        }
        else -> {
        }
      }
    }
  }

  private fun unloadGroupElement(element: XmlElement) {
    val id = element.attributes.get(ID_ATTR_NAME) ?: throw IllegalStateException("Cannot unload groups with no ID")
    for (groupChild in element.children) {
      if (groupChild.name == ACTION_ELEMENT_NAME) {
        unloadActionElement(groupChild)
      }
      else if (groupChild.name == GROUP_ELEMENT_NAME) {
        unloadGroupElement(groupChild)
      }
    }
    unregisterAction(id)
  }

  private fun unloadActionElement(element: XmlElement) {
    val className = element.attributes.get(CLASS_ATTR_NAME)
    val overrides = element.attributes.get(OVERRIDES_ATTR_NAME).toBoolean()
    val id = obtainActionId(element = element, className = className)
    if (overrides) {
      val baseAction = actionPostInitRegistrar.state.baseActions.get(id)
      if (baseAction != null) {
        replaceAction(id, baseAction)
        actionPostInitRegistrar.state.baseActions.remove(id)
        return
      }
    }
    unregisterAction(id)
  }

  override fun registerAction(actionId: String, action: AnAction, pluginId: PluginId?) {
    synchronized(actionPostInitRegistrar.state.lock) {
      registerAction(actionId = actionId,
                     action = action,
                     pluginId = pluginId,
                     projectType = null,
                     actionRegistrar = actionPostInitRegistrar)
    }
  }

  override fun registerAction(actionId: String, action: AnAction) {
    synchronized(actionPostInitRegistrar.state.lock) {
      registerAction(actionId = actionId, action = action, pluginId = null, projectType = null, actionRegistrar = actionPostInitRegistrar)
    }
  }

  override fun unregisterAction(actionId: String) {
    synchronized(actionPostInitRegistrar.state.lock) {
      unregisterAction(actionId = actionId, actionRegistrar = actionPostInitRegistrar)
    }
  }

  /**
   * Unregisters already registered action and prevented the action from being registered in the future.
   * Should be used only in IDE configuration
   */
  @Internal
  fun prohibitAction(actionId: String) {
    val state = actionPostInitRegistrar.state
    synchronized(state.lock) {
      state.prohibitedActionIds = HashSet(state.prohibitedActionIds).let {
        it.add(actionId)
        it
      }
    }
    val action = getAction(actionId)
    if (action != null) {
      AbbreviationManager.getInstance().removeAllAbbreviations(actionId)
      unregisterAction(actionId)
    }
  }

  @TestOnly
  fun resetProhibitedActions() {
    synchronized(actionPostInitRegistrar.state.lock) {
      actionPostInitRegistrar.state.prohibitedActionIds = java.util.Set.of()
    }
  }

  override val registrationOrderComparator: Comparator<String>
    get() {
      val idToDescriptor = actionPostInitRegistrar.state.idToDescriptor
      return Comparator.comparingInt { key -> idToDescriptor.get(key)?.index ?: -1 }
    }

  override fun getPluginActions(pluginId: PluginId): Array<String> {
    return actionPostInitRegistrar.state.getPluginActions(pluginId).toTypedArray()
  }

  fun addActionPopup(menu: Any) {
    popups.add(menu)
    if (menu is ActionPopupMenu) {
      for (listener in actionPopupMenuListeners) {
        listener.actionPopupMenuCreated(menu)
      }
    }
  }

  fun removeActionPopup(menu: Any) {
    val removed = popups.remove(menu)
    if (removed && menu is ActionPopupMenu) {
      for (listener in actionPopupMenuListeners) {
        listener.actionPopupMenuReleased(menu)
      }
    }
  }

  override val isActionPopupStackEmpty: Boolean
    get() = popups.isEmpty()

  final override fun addActionPopupMenuListener(listener: ActionPopupMenuListener, parentDisposable: Disposable) {
    actionPopupMenuListeners.add(listener)
    Disposer.register(parentDisposable) { actionPopupMenuListeners.remove(listener) }
  }

  final override fun replaceAction(actionId: String, newAction: AnAction) {
    val plugin = walker.callerClass?.let { PluginManager.getPluginByClass(it) }
    synchronized(actionPostInitRegistrar.state.lock) {
      replaceAction(actionId = actionId, newAction = newAction, pluginId = plugin?.pluginId, actionRegistrar = actionPostInitRegistrar)
    }
  }

  /**
   * Returns the action overridden by the specified overriding action (with overrides="true" in plugin.xml).
   */
  fun getBaseAction(overridingAction: OverridingAction): AnAction? = actionPostInitRegistrar.getBaseAction(overridingAction)

  fun getParentGroupIds(actionId: String): Collection<String> = actionPostInitRegistrar.state.getParentGroupIds(actionId)

  @Suppress("removal", "OVERRIDE_DEPRECATION")
  override fun addAnActionListener(listener: AnActionListener) {
    actionListeners.add(listener)
  }

  override fun fireBeforeActionPerformed(action: AnAction, event: AnActionEvent) {
    prevPreformedActionId = lastPreformedActionId
    lastPreformedActionId = getId(action)
    if (lastPreformedActionId == null && action is ActionIdProvider) {
      lastPreformedActionId = (action as ActionIdProvider).id
    }
    IdeaLogger.ourLastActionId = lastPreformedActionId
    ProhibitAWTEvents.start("fireBeforeActionPerformed").use {
      for (listener in actionListeners) {
        listener.beforeActionPerformed(action, event)
      }
      publisher().beforeActionPerformed(action, event)
      onBeforeActionInvoked(action, event)
    }
  }

  override fun fireAfterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
    prevPreformedActionId = lastPreformedActionId
    lastPreformedActionId = getId(action)
    IdeaLogger.ourLastActionId = lastPreformedActionId
    ProhibitAWTEvents.start("fireAfterActionPerformed").use {
      onAfterActionInvoked(action, event, result)
      for (listener in actionListeners) {
        listener.afterActionPerformed(action, event, result)
      }
      publisher().afterActionPerformed(action, event, result)
    }
  }

  final override fun getKeyboardShortcut(actionId: String): KeyboardShortcut? {
    val action = getAction(actionId) ?: return null
    for (shortcut in action.shortcutSet.shortcuts) {
      // Shortcut can be a MouseShortcut here.
      // For example, `IdeaVIM` often assigns them
      if (shortcut is KeyboardShortcut && shortcut.secondKeyStroke == null) {
        return shortcut
      }
    }
    return null
  }

  override fun fireBeforeEditorTyping(c: Char, dataContext: DataContext) {
    lastTimeEditorWasTypedIn = System.currentTimeMillis()
    for (listener in actionListeners) {
      listener.beforeEditorTyping(c, dataContext)
    }
    //maybe readaction
    WriteIntentReadAction.run {
      publisher().beforeEditorTyping(c, dataContext)
    }
  }

  override fun fireAfterEditorTyping(c: Char, dataContext: DataContext) {
    for (listener in actionListeners) {
      listener.afterEditorTyping(c, dataContext)
    }
    //maybe readaction
    WriteIntentReadAction.run {
      publisher().afterEditorTyping(c, dataContext)
    }
  }

  val actionIds: Set<String>
    get() = actionPostInitRegistrar.ids

  @Internal
  fun actions(canReturnStub: Boolean): Sequence<AnAction> {
    if (canReturnStub) {
      // return snapshot
      return actionPostInitRegistrar.actions.asSequence()
    }
    else {
      return actionPostInitRegistrar.ids.asSequence()
        .mapNotNull {
          getAction(id = it, canReturnStub = false, actionRegistrar = actionPostInitRegistrar)
        }
    }
  }

  override fun performWithActionCallbacks(action: AnAction,
                                          event: AnActionEvent,
                                          runnable: Runnable) {
    val project = event.project
    PerformWithDocumentsCommitted.commitDocumentsIfNeeded(action, event)
    fireBeforeActionPerformed(action, event)
    val component = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    val actionId = getId(action)
                   ?: if (action is EmptyAction) runnable.javaClass.name else action.javaClass.name
    if (component != null && !UIUtil.isShowing(component) &&
        event.place != ActionPlaces.TOUCHBAR_GENERAL &&
        ClientProperty.get(component, ActionUtil.ALLOW_ACTION_PERFORM_WHEN_HIDDEN) != true) {
      LOG.warn("Action is not performed because target component is not showing: " +
               "action=$actionId, component=${component.javaClass.name}")
      fireAfterActionPerformed(action, event, AnActionResult.IGNORED)
      return
    }
    val container =
      if (!event.presentation.isApplicationScope && project is ComponentManagerImpl) project
      else ApplicationManager.getApplication() as ComponentManagerImpl
    val cs = container.pluginCoroutineScope(action.javaClass.classLoader)
    val coroutineName = CoroutineName("${action.javaClass.name}#actionPerformed@${event.place}")
    // save stack frames using an explicit continuation trick & inline blockingContext
    lateinit var continuation: CancellableContinuation<Unit>
    cs.launch(Dispatchers.Unconfined + coroutineName, CoroutineStart.UNDISPATCHED) {
      suspendCancellableCoroutine { continuation = it }
    }
    val result = try {
      val coroutineContext = continuation.context +
                             ModalityState.current().asContextElement() +
                             ClientId.coroutineContext() +
                             ActionContextElement.create(actionId, event.place, event.inputEvent, component)
      installThreadContext(coroutineContext.minusKey(ContinuationInterceptor), replace = true).use { _ ->
        SlowOperations.startSection(SlowOperations.ACTION_PERFORM).use { _ ->
          runnable.run()
        }
      }
      AnActionResult.PERFORMED
    }
    catch (ex: Throwable) {
      AnActionResult.failed(ex)
    }
    finally {
      continuation.resume(Unit)
    }
    try {
      fireAfterActionPerformed(action, event, result)
    }
    catch (ex: Throwable) {
      if (result.isPerformed) throw ex
      else result.failureCause.addSuppressed(ex)
    }
    when {
      result.isPerformed -> Unit
      result.failureCause is IndexNotReadyException -> {
        LOG.info(result.failureCause)
        showDumbModeWarning(project, action, event)
      }
      else -> throw result.failureCause
    }
  }

  @TestOnly
  fun preloadActions() {
    for (id in actionPostInitRegistrar.ids) {
      getAction(id = id, canReturnStub = false, actionRegistrar = actionPostInitRegistrar)
      // don't preload ActionGroup.getChildren() because that would un-stub child actions
      // and make it impossible to replace the corresponding actions later
      // (via unregisterAction+registerAction, as some app components do)
    }
  }

  override fun tryToExecute(action: AnAction,
                            inputEvent: InputEvent?,
                            contextComponent: Component?,
                            place: String?,
                            now: Boolean): ActionCallback {
    ThreadingAssertions.assertEventDispatchThread()
    val result = ActionCallback()
    val place = place ?: "tryToExecute"
    if (now) {
      try {
        tryToExecuteNow(action, place, contextComponent, inputEvent, result)
      }
      finally {
        if (!result.isProcessed) {
          result.setRejected()
        }
      }
    }
    else {
      service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.EDT) {
        try {
          tryToExecuteSuspend(action, place, contextComponent, inputEvent, result)
        }
        finally {
          if (!result.isProcessed) {
            blockingContext {
              result.setRejected()
            }
          }
        }
      }
    }
    return result
  }

  private val _timerEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_LATEST)

  @Internal
  @Experimental
  override val timerEvents: Flow<Unit> = _timerEvents.asSharedFlow()

  private inner class MyTimer(private val coroutineScope: CoroutineScope) {
    @JvmField
    val listeners: MutableList<TimerListener> = ContainerUtil.createLockFreeCopyOnWriteList()

    private var lastTimePerformed = 0
    private val clientId = ClientId.current

    init {
      val connection = ApplicationManager.getApplication().messageBus.simpleConnect()

      val delayFlow = MutableSharedFlow<Duration>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

      connection.subscribe(ApplicationActivationListener.TOPIC, object : ApplicationActivationListener {
        override fun applicationActivated(ideFrame: IdeFrame) {
          delayFlow.tryEmit(TIMER_DELAY.milliseconds)
        }

        override fun applicationDeactivated(ideFrame: IdeFrame) {
          delayFlow.tryEmit(DEACTIVATED_TIMER_DELAY.milliseconds)
        }
      })

      delayFlow.tryEmit(TIMER_DELAY.milliseconds)

      coroutineScope.launch {
        delayFlow.collectLatest { delay ->
          while (true) {
            delay(delay)
            // RawSwingDispatcher - as old javax.swing.Timer does
            withContext(RawSwingDispatcher) {
              tick()
            }
          }
        }
      }
    }

    fun stop() {
      coroutineScope.cancel()
    }

    private fun tick() {
      if (lastTimeEditorWasTypedIn + UPDATE_DELAY_AFTER_TYPING > System.currentTimeMillis()) {
        return
      }

      val lastEventCount = lastTimePerformed
      lastTimePerformed = ActivityTracker.getInstance().count
      if (lastTimePerformed == lastEventCount) {
        return
      }

      _timerEvents.tryEmit(Unit)

      @Suppress("ForbiddenInSuspectContextMethod")
      withClientId(clientId).use {
        for (listener in listeners) {
          runListenerAction(listener)
        }
      }
    }
  }
}

private fun doPerformAction(action: AnAction,
                            event: AnActionEvent,
                            result: ActionCallback) {
  (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity {
    ActionUtil.lastUpdateAndCheckDumb(action, event, false)
    if (!event.presentation.isEnabled) {
      result.setRejected()
      return@performUserActivity
    }
    addAwtListener(AWTEvent.WINDOW_EVENT_MASK, result) {
      if (it.id == WindowEvent.WINDOW_OPENED || it.id == WindowEvent.WINDOW_ACTIVATED) {
        if (!result.isProcessed) {
          val we = it as WindowEvent
          IdeFocusManager.findInstanceByComponent(we.window).doWhenFocusSettlesDown(
            result.createSetDoneRunnable(), ModalityState.defaultModalityState())
        }
      }
    }
    try {
      ActionUtil.performActionDumbAwareWithCallbacks(action, event)
    }
    finally {
      result.setDone()
    }
  }
}

private fun tryToExecuteNow(action: AnAction,
                            place: String,
                            contextComponent: Component?,
                            inputEvent: InputEvent?,
                            result: ActionCallback) {
  val presentationFactory = PresentationFactory()
  val dataContext = DataManager.getInstance().run {
    if (contextComponent == null) dataContext else getDataContext(contextComponent)
  }
  val wrappedContext = Utils.createAsyncDataContext(dataContext)
  val componentAdjusted = PlatformDataKeys.CONTEXT_COMPONENT.getData(wrappedContext) ?: contextComponent
  val actionProcessor = object : ActionProcessor() {}
  val inputEventAdjusted = inputEvent ?: KeyEvent(
    componentAdjusted, KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_UNDEFINED, '\u0000')
  val event = Utils.runWithInputEventEdtDispatcher(componentAdjusted) block@{
    Utils.runUpdateSessionForInputEvent(
      listOf(action), inputEventAdjusted, wrappedContext, place, actionProcessor, presentationFactory) { rearranged, updater, events ->
      val presentation = updater(action)
      val event = events[presentation]
      if (event == null || !presentation.isEnabled) {
        null
      }
      else {
        UpdateResult(action, event, 0L)
      }
    }
  }?.event
  if (event != null && event.presentation.isEnabled) {
    doPerformAction(action, event, result)
  }
}

private suspend fun tryToExecuteSuspend(action: AnAction,
                                        place: String,
                                        contextComponent: Component?,
                                        inputEvent: InputEvent?,
                                        result: ActionCallback) {
  (if (contextComponent != null) IdeFocusManager.findInstanceByComponent(contextComponent)
  else IdeFocusManager.getGlobalInstance()).awaitFocusSettlesDown()

  val dataContext = DataManager.getInstance().run {
    if (contextComponent == null) dataContext else getDataContext(contextComponent)
  }
  val wrappedContext = Utils.createAsyncDataContext(dataContext)

  val presentationFactory = PresentationFactory()
  Utils.expandActionGroupSuspend(DefaultActionGroup(action), presentationFactory, wrappedContext, place, false, false)
  val presentation = presentationFactory.getPresentation(action)
  val event = if (presentation.isEnabled) AnActionEvent(
    inputEvent, wrappedContext, place, presentation, ActionManager.getInstance(), 0, false, false)
  else null
  if (event != null && event.presentation.isEnabled) {
    //todo fix all clients and move locks into them
    writeIntentReadAction {
      doPerformAction(action, event, result)
    }
  }
}

private class CapturingListener(@JvmField val timerListener: TimerListener) : TimerListener by timerListener {
  val childContext: ChildContext = createChildContext("ActionManager: $timerListener")

  override fun run() {
    // this is periodic runnable that is invoked on timer; it should not complete a parent job
    childContext.runInChildContext(completeOnFinish = false, {
      timerListener.run()
    })
  }
}

private fun runListenerAction(listener: TimerListener) {
  val modalityState = listener.modalityState ?: return
  LOG.debug { "notify $listener" }
  if (!ModalityState.current().dominates(modalityState)) {
    runCatching {
      listener.run()
    }.getOrLogException(LOG)
  }
}

private val DYNAMIC_EP_NAME = ExtensionPointName<DynamicActionConfigurationCustomizer>("com.intellij.dynamicActionConfigurationCustomizer")

private val LOG = logger<ActionManagerImpl>()

private const val ACTION_ELEMENT_NAME = "action"
private const val GROUP_ELEMENT_NAME = "group"
private const val CLASS_ATTR_NAME = "class"
private const val ID_ATTR_NAME = "id"
private const val INTERNAL_ATTR_NAME = "internal"
private const val ICON_ATTR_NAME = "icon"
private const val ADD_TO_GROUP_ELEMENT_NAME = "add-to-group"
private const val DESCRIPTION = "description"
private const val TEXT_ATTR_NAME = "text"
private const val KEY_ATTR_NAME = "key"
private const val SEPARATOR_ELEMENT_NAME = "separator"
private const val REFERENCE_ELEMENT_NAME = "reference"
private const val GROUP_ID_ATTR_NAME = "group-id"
private const val REF_ATTR_NAME = "ref"
private const val USE_SHORTCUT_OF_ATTR_NAME = "use-shortcut-of"
private const val PROJECT_TYPE = "project-type"
private const val OVERRIDE_TEXT_ELEMENT_NAME = "override-text"
private const val SYNONYM_ELEMENT_NAME = "synonym"
private const val OVERRIDES_ATTR_NAME = "overrides"
private const val DEACTIVATED_TIMER_DELAY = 5000
private const val TIMER_DELAY = 500
private const val UPDATE_DELAY_AFTER_TYPING = 500

private fun publisher(): AnActionListener {
  return ApplicationManager.getApplication().messageBus.syncPublisher(AnActionListener.TOPIC)
}

private fun <T> instantiate(stubClassName: String,
                            pluginDescriptor: PluginDescriptor,
                            expectedClass: Class<T>,
                            componentManager: ComponentManager): T? {
  val obj = try {
    componentManager.instantiateClass<Any>(stubClassName, pluginDescriptor)
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: ProcessCanceledException) {
    throw e
  }
  catch (e: ExtensionNotApplicableException) {
    return null
  }
  catch (e: Throwable) {
    LOG.error(e)
    return null
  }

  if (expectedClass.isInstance(obj)) {
    @Suppress("UNCHECKED_CAST")
    return obj as T
  }
  LOG.error(PluginException("class with name '$stubClassName' must be an instance of '${expectedClass.name}'; " +
                            "got $obj", pluginDescriptor.pluginId))
  return null
}

private fun updateIconFromStub(stub: ActionStubBase, anAction: AnAction, componentManager: ComponentManager) {
  val iconPath = stub.iconPath
  if (iconPath != null) {
    val module = stub.plugin
    val requestor = anAction.javaClass.name
    anAction.templatePresentation.setIconSupplier(SynchronizedClearableLazy {
      loadIcon(module = module, iconPath = iconPath, requestor = requestor)
    })
  }

  val customActionsSchema = componentManager.serviceIfCreated<CustomActionsSchema>()
  if (customActionsSchema != null && !customActionsSchema.getIconPath(stub.id).isEmpty()) {
    RecursionManager.doPreventingRecursion<Any?>(stub.id, false) {
      customActionsSchema.initActionIcon(anAction = anAction, actionId = stub.id, actionManager = ActionManager.getInstance())
      null
    }
  }
}

private fun convertGroupStub(stub: ActionGroupStub, actionRegistrar: ActionRegistrar): ActionGroup? {
  val componentManager = ApplicationManager.getApplication()
  val group = if (stub.actionClass === DEFAULT_ACTION_GROUP_CLASS_NAME) {
    DefaultActionGroup()
  }
  else {
    instantiate(stubClassName = stub.actionClass,
                pluginDescriptor = stub.plugin,
                expectedClass = ActionGroup::class.java,
                componentManager = componentManager)
    ?: return null
  }
  stub.initGroup(target = group, actionToId = { action ->
    if (action is ActionStubBase) {
      action.id
    }
    else {
      synchronized(actionRegistrar.state.lock) {
        actionRegistrar.state.actionToId.get(action)
      }
    }
  })
  updateIconFromStub(stub = stub, anAction = group, componentManager = componentManager)
  return group
}

private fun processAbbreviationNode(e: XmlElement, id: String) {
  val abbr = e.attributes.get("value")
  if (!abbr.isNullOrEmpty()) {
    val abbreviationManager = AbbreviationManager.getInstance() as AbbreviationManagerImpl
    abbreviationManager.register(abbr, id, true)
  }
}

private fun isSecondary(element: XmlElement): Boolean = element.attributes.get("secondary").toBoolean()

private fun loadIcon(module: PluginDescriptor, iconPath: String, requestor: String?): Icon {
  val start = StartUpMeasurer.getCurrentTimeIfEnabled()
  var icon = findIconUsingNewImplementation(path = iconPath, classLoader = module.classLoader)
  if (icon == null) {
    reportActionError(module, "Icon cannot be found in '$iconPath', action '$requestor'")
    icon = AllIcons.Nodes.Unknown
  }
  IconLoadMeasurer.actionIcon.end(start)
  return icon
}

@Suppress("HardCodedStringLiteral")
private fun computeDescription(bundleSupplier: () -> ResourceBundle?,
                               id: String,
                               elementType: String,
                               descriptionValue: String?,
                               classLoader: ClassLoader): @NlsActions.ActionDescription String? {
  var effectiveBundle = bundleSupplier()
  if (effectiveBundle != null && DefaultBundleService.isDefaultBundle()) {
    effectiveBundle = DynamicBundle.getResourceBundle(classLoader, effectiveBundle.baseBundleName)
  }
  return AbstractBundle.messageOrDefault(effectiveBundle, "$elementType.$id.$DESCRIPTION", descriptionValue ?: "")
}

@Suppress("HardCodedStringLiteral")
private fun computeActionText(bundleSupplier: () -> ResourceBundle?,
                              id: String,
                              elementType: String,
                              textValue: String?,
                              classLoader: ClassLoader): @NlsActions.ActionText String? {
  var effectiveBundle = bundleSupplier()
  if (effectiveBundle != null && DefaultBundleService.isDefaultBundle()) {
    effectiveBundle = DynamicBundle.getResourceBundle(classLoader, effectiveBundle.baseBundleName)
  }
  if (effectiveBundle == null) {
    return textValue
  }
  else {
    // messageOrDefault doesn't like default value as null
    // (it counts it as a lack of default value, that's why we use empty string instead of null)
    return AbstractBundle.messageOrDefault(effectiveBundle, "$elementType.$id.$TEXT_ATTR_NAME", textValue ?: "")?.takeIf { it.isNotEmpty() }
  }
}

private fun parseAnchor(anchorStr: String?, actionName: String?, module: IdeaPluginDescriptor): Anchor? {
  return when {
    anchorStr == null -> Anchor.LAST
    "first".equals(anchorStr, ignoreCase = true) -> Anchor.FIRST
    "last".equals(anchorStr, ignoreCase = true) -> Anchor.LAST
    "before".equals(anchorStr, ignoreCase = true) -> Anchor.BEFORE
    "after".equals(anchorStr, ignoreCase = true) -> Anchor.AFTER
    else -> {
      reportActionError(module,
                        "$actionName: anchor should be one of the following constants: \"first\", \"last\", \"before\" or \"after\"")
      null
    }
  }
}

private fun reportActionError(module: PluginDescriptor, message: String, cause: Throwable? = null) {
  LOG.error(PluginException("$message (module=$module)", cause, module.pluginId))
}

private fun getPluginInfo(id: PluginId?): String {
  val plugin = (if (id == null) null else PluginManagerCore.getPlugin(id)) ?: return ""
  return " (Plugin: ${plugin.name ?: id!!.idString})"
}

private fun createActionToolbarImpl(place: String,
                                    group: ActionGroup,
                                    horizontal: Boolean,
                                    decorateButtons: Boolean,
                                    customizable: Boolean): ActionToolbarImpl {
  return ActionToolbarImpl(place, group, horizontal, decorateButtons, customizable)
}

private fun obtainActionId(element: XmlElement, className: String?): String {
  val id = element.attributes.get(ID_ATTR_NAME)
  return if (id.isNullOrEmpty()) StringUtilRt.getShortName(className!!) else id
}

private fun processOverrideTextNode(action: AnAction,
                                    id: String,
                                    element: XmlElement,
                                    module: IdeaPluginDescriptor, bundleSupplier: () -> ResourceBundle?) {
  val place = element.attributes.get("place")
  if (place == null) {
    reportActionError(module, "$id: override-text specified without place")
    return
  }

  val useTextOfPlace = element.attributes.get("use-text-of-place")
  if (useTextOfPlace != null) {
    action.copyActionTextOverride(useTextOfPlace, place, id)
  }
  else {
    val bundle = bundleSupplier()
    val text = element.attributes.get(TEXT_ATTR_NAME)
    if (text.isNullOrEmpty() && bundle != null) {
      val prefix = if (action is ActionGroup) "group" else "action"
      val key = "$prefix.$id.$place.text"
      action.addTextOverride(place) { BundleBase.message(bundleSupplier()!!, key) }
    }
    else {
      action.addTextOverride(place) { text }
    }
  }
}

private fun processSynonymNode(action: AnAction, element: XmlElement, module: IdeaPluginDescriptor, bundleSupplier: () -> ResourceBundle?) {
  val text = element.attributes.get(TEXT_ATTR_NAME)
  if (!text.isNullOrEmpty()) {
    action.addSynonym { text }
  }
  else {
    val key = element.attributes.get(KEY_ATTR_NAME)
    if (key != null && bundleSupplier() != null) {
      action.addSynonym { BundleBase.message(bundleSupplier()!!, key) }
    }
    else {
      reportActionError(module, "Can't process synonym: neither text nor resource bundle key is specified")
    }
  }
}

private fun createSeparator(bundleSupplier: () -> ResourceBundle?, key: String): Separator {
  val bundle = bundleSupplier()
  val text = if (bundle == null) null else AbstractBundle.messageOrNull(bundle, key)
  return if (text == null) Separator.getInstance() else Separator(text)
}

private fun getReferenceActionId(element: XmlElement): String? {
  // support old style references by id
  return element.attributes.get(REF_ATTR_NAME) ?: element.attributes.get(ID_ATTR_NAME)
}

internal fun canUnloadActionGroup(element: XmlElement): Boolean {
  if (element.attributes[ID_ATTR_NAME] == null) {
    return false
  }
  for (child in element.children) {
    if (child.name == GROUP_ELEMENT_NAME && !canUnloadActionGroup(child)) {
      return false
    }
  }
  return true
}

private fun updateHandlers(action: Any?) {
  if (action is EditorAction) {
    action.clearDynamicHandlersCache()
  }
}

internal fun convertStub(stub: ActionStub): AnAction? {
  val componentManager = ApplicationManager.getApplication() ?: throw AlreadyDisposedException("Application is already disposed")
  val anAction = instantiate(stubClassName = stub.className,
                             pluginDescriptor = stub.plugin,
                             expectedClass = AnAction::class.java,
                             componentManager = componentManager)
                 ?: return null
  stub.initAction(anAction)
  updateIconFromStub(stub = stub, anAction = anAction, componentManager = componentManager)
  return anAction
}

private fun configureGroupDescriptionAndIcon(presentation: Presentation,
                                             @NlsSafe description: String?,
                                             textValue: String?,
                                             group: ActionGroup,
                                             bundleSupplier: () -> ResourceBundle?,
                                             id: String,
                                             classLoader: ClassLoader,
                                             iconPath: String?,
                                             module: IdeaPluginDescriptorImpl,
                                             className: String?) {
  // don't override value which was set in API with empty value from xml descriptor
  presentation.setFallbackPresentationText {
    computeActionText(bundleSupplier = bundleSupplier, id = id, elementType = GROUP_ELEMENT_NAME, textValue = textValue, classLoader = classLoader)
  }

  // description
  if (bundleSupplier() == null) {
    // don't override value which was set in API with empty value from xml descriptor
    if (!description.isNullOrEmpty() || presentation.description == null) {
      presentation.description = description
    }
  }
  else {
    val descriptionSupplier = {
      computeDescription(bundleSupplier = bundleSupplier,
                         id = id,
                         elementType = GROUP_ELEMENT_NAME,
                         descriptionValue = description,
                         classLoader = classLoader)
    }
    // don't override value which was set in API with empty value from xml descriptor
    if (!descriptionSupplier().isNullOrEmpty() || presentation.description == null) {
      presentation.setDescription(descriptionSupplier)
    }
  }

  if (iconPath != null && group !is ActionGroupStub) {
    presentation.setIconSupplier(SynchronizedClearableLazy {
      loadIcon(module = module, iconPath = iconPath, requestor = className)
    })
  }
}

/**
 * Executed under lock.
 * @return true on success, false on an action conflict
 */
private fun addToMap(actionId: String,
                     existing: AnAction?,
                     action: AnAction,
                     projectType: ProjectType?,
                     registrar: ActionRegistrar): Boolean {
  if (existing is ChameleonAction) {
    return existing.addAction(action, projectType)
  }
  else if (existing != null) {
    // we need to create ChameleonAction even if 'projectType==null', in case 'ActionStub.getProjectType() != null'
    val chameleonAction = ChameleonAction(existing, null)
    if (!chameleonAction.addAction(action, projectType)) {
      return false
    }

    registrar.putAction(actionId, chameleonAction)
    return true
  }
  else if (projectType != null) {
    val chameleonAction = ChameleonAction(action, projectType)
    registrar.putAction(actionId, chameleonAction)
    return true
  }
  else {
    registrar.putAction(actionId, action)
    return true
  }
}

@Internal
private sealed interface ActionRegistrar {
  val state: ActionManagerState

  val isPostInit: Boolean

  fun putAction(actionId: String, action: AnAction)

  fun removeAction(actionId: String)

  fun getAction(id: String): AnAction?

  fun bindShortcuts(sourceActionId: String, targetActionId: String)

  fun unbindShortcuts(targetActionId: String)

  fun getActionBinding(actionId: String): String?

  fun actionRegistered(actionId: String, action: AnAction)
}

private class PostInitActionRegistrar(
  idToAction: Map<String, AnAction>,
  @Volatile private var boundShortcuts: Map<String, String>,
  override val state: ActionManagerState,
) : ActionRegistrar {
  private val idToAction = ConcurrentHashMap(idToAction)

  val ids: Set<String>
    get() = idToAction.keys

  override val isPostInit: Boolean
    get() = true

  /**
   * Stub actions here! Don't use it blindly.
   */
  val actions: Collection<AnAction>
    get() = idToAction.values

  override fun putAction(actionId: String, action: AnAction) {
    idToAction.put(actionId, action)
  }

  override fun removeAction(actionId: String) {
    idToAction.remove(actionId)
  }

  override fun getAction(id: String) = idToAction.get(id)

  fun getBaseAction(overridingAction: OverridingAction): AnAction? {
    val id = getId(overridingAction as AnAction) ?: return null
    synchronized(state.lock) {
      return state.baseActions.get(id)
    }
  }

  fun getId(action: AnAction): String? {
    if (action is ActionStubBase) {
      return action.id
    }
    synchronized(state.lock) {
      return state.actionToId.get(action)
    }
  }

  fun getActionIdList(idPrefix: String): List<String> {
    return idToAction.keys.filter { it.startsWith(idPrefix) }
  }

  fun actionsOrStubs(): Sequence<AnAction> = idToAction.values.asSequence()

  fun unstubbedActions(filter: (String) -> Boolean): Sequence<AnAction> {
    return idToAction.keys.asSequence().filter(filter).mapNotNull {
      getAction(id = it, canReturnStub = false, actionRegistrar = this)
    }
  }

  fun groupIds(actionId: String): List<String> {
    return state.idToDescriptor[actionId]?.groupIds ?: emptyList()
  }

  fun getBoundActions(): Set<String> = boundShortcuts.keys

  override fun getActionBinding(actionId: String) = getActionBinding(actionId, boundShortcuts)

  override fun bindShortcuts(sourceActionId: String, targetActionId: String) {
    boundShortcuts = boundShortcuts.with(targetActionId, sourceActionId)
  }

  override fun unbindShortcuts(targetActionId: String) {
    boundShortcuts = boundShortcuts.without(targetActionId)
  }

  override fun actionRegistered(actionId: String, action: AnAction) {
    val schema = ApplicationManager.getApplication().serviceIfCreated<CustomActionsSchema>() ?: return
    for (url in schema.getActions()) {
      if (url.componentId == actionId) {
        schema.incrementModificationStamp()
        break
      }
    }

    updateHandlers(action)
  }
}

private class PreInitActionRuntimeRegistrar(
  private val idToAction: HashMap<String, AnAction>,
  private val actionRegistrar: ActionPreInitRegistrar,
) : ActionRuntimeRegistrar {
  override fun unregisterActionByIdPrefix(idPrefix: String) {
    for (oldId in idToAction.keys.filter { it.startsWith(idPrefix) }) {
      unregisterAction(actionId = oldId, actionRegistrar = actionRegistrar)
    }
  }

  override fun unregisterAction(actionId: String) {
    unregisterAction(actionId = actionId, actionRegistrar = actionRegistrar)
  }

  override fun getActionOrStub(actionId: String): AnAction? = idToAction.get(actionId)

  override fun getUnstubbedAction(actionId: String): AnAction? {
    return getAction(id = actionId, canReturnStub = false, actionRegistrar = actionRegistrar)
  }

  override fun addToGroup(group: AnAction, action: AnAction, constraints: Constraints) {
    addToGroup(group = group,
               action = action,
               constraints = constraints,
               module = null,
               state = actionRegistrar.state,
               secondary = false)
  }

  override fun replaceAction(actionId: String, newAction: AnAction) {
    val plugin = walker.callerClass?.let { PluginManager.getPluginByClass(it) }
    replaceAction(actionId = actionId, newAction = newAction, pluginId = plugin?.pluginId, actionRegistrar = actionRegistrar)
  }

  override fun getId(action: AnAction): String? {
    return if (action is ActionStubBase) action.id else actionRegistrar.state.actionToId.get(action)
  }

  override fun getBaseAction(overridingAction: OverridingAction): AnAction? {
    val id = getId(overridingAction as AnAction) ?: return null
    return actionRegistrar.state.baseActions.get(id)
  }

  override fun registerAction(actionId: String, action: AnAction) {
    registerAction(actionId = actionId,
                   action = action,
                   pluginId = null,
                   projectType = null,
                   actionRegistrar = actionRegistrar)
  }
}

private class PostInitActionRuntimeRegistrar(private val actionPostInitRegistrar: PostInitActionRegistrar) : ActionRuntimeRegistrar {
  override fun registerAction(actionId: String, action: AnAction) {
    val plugin = walker.callerClass?.let { PluginManager.getPluginByClass(it) }
    synchronized(actionPostInitRegistrar.state.lock) {
      registerAction(actionId = actionId,
                     action = action,
                     pluginId = plugin?.pluginId,
                     projectType = null,
                     actionRegistrar = actionPostInitRegistrar)
    }
  }

  override fun unregisterActionByIdPrefix(idPrefix: String) {
    for (oldId in actionPostInitRegistrar.getActionIdList(idPrefix)) {
      synchronized(actionPostInitRegistrar.state.lock) {
        unregisterAction(actionId = oldId, actionRegistrar = actionPostInitRegistrar)
      }
    }
  }

  override fun unregisterAction(actionId: String) {
    synchronized(actionPostInitRegistrar.state.lock) {
      unregisterAction(actionId = actionId, actionRegistrar = actionPostInitRegistrar)
    }
  }

  override fun getActionOrStub(actionId: String): AnAction? = actionPostInitRegistrar.getAction(actionId)

  override fun getUnstubbedAction(actionId: String): AnAction? {
    return getAction(id = actionId, canReturnStub = false, actionRegistrar = actionPostInitRegistrar)
  }

  override fun addToGroup(group: AnAction, action: AnAction, constraints: Constraints) {
    addToGroup(group = group as DefaultActionGroup,
               action = action,
               constraints = constraints,
               module = null,
               state = actionPostInitRegistrar.state,
               secondary = false)
  }

  override fun replaceAction(actionId: String, newAction: AnAction) {
    val plugin = walker.callerClass?.let { PluginManager.getPluginByClass(it) }
    synchronized(actionPostInitRegistrar.state.lock) {
      replaceAction(actionId = actionId, newAction = newAction, pluginId = plugin?.pluginId, actionRegistrar = actionPostInitRegistrar)
    }
  }

  override fun getId(action: AnAction): String? = actionPostInitRegistrar.getId(action)

  override fun getBaseAction(overridingAction: OverridingAction): AnAction? = actionPostInitRegistrar.getBaseAction(overridingAction)

}

private fun getActionBinding(actionId: String, boundShortcuts: Map<String, String>): String? {
  var visited: MutableSet<String>? = null
  var id = actionId
  while (true) {
    val next = boundShortcuts.get(id) ?: break
    if (visited == null) {
      visited = HashSet()
    }

    id = next
    if (!visited.add(id)) {
      break
    }
  }
  return if (id == actionId) null else id
}

private class ActionPreInitRegistrar(
  private val idToAction: HashMap<String, AnAction>,
  private val boundShortcuts: HashMap<String, String>,
  override val state: ActionManagerState,
) : ActionRegistrar {
  override val isPostInit: Boolean
    get() = false

  override fun putAction(actionId: String, action: AnAction) {
    idToAction.put(actionId, action)
  }

  override fun removeAction(actionId: String) {
    idToAction.remove(actionId)
  }

  override fun getAction(id: String) = idToAction.get(id)

  override fun bindShortcuts(sourceActionId: String, targetActionId: String) {
    boundShortcuts.put(targetActionId, sourceActionId)
  }

  override fun unbindShortcuts(targetActionId: String) {
    boundShortcuts.remove(targetActionId)
  }

  override fun getActionBinding(actionId: String) = getActionBinding(actionId, boundShortcuts)

  override fun actionRegistered(actionId: String, action: AnAction) {
  }
}

private fun reportActionIdCollision(actionId: String,
                                    action: AnAction,
                                    pluginId: PluginId?,
                                    oldAction: AnAction?,
                                    idToDescriptor: MutableMap<String, ActionManagerStateActionItemDescriptor>) {
  val oldPluginInfo = idToDescriptor.get(actionId)?.pluginId?.let { getPluginInfo(it) }
  val message = "ID '$actionId' is already taken by action ${actionToString(oldAction)} $oldPluginInfo. " +
                "Action ${actionToString(action)} cannot use the same ID"
  if (pluginId == null) {
    LOG.error(message)
  }
  else {
    LOG.error(PluginException("$message (plugin $pluginId)", null, pluginId))
  }
}

private fun actionToString(action: AnAction?): @NonNls String {
  if (action == null) return "null"
  when (action) {
    is ChameleonAction -> return "ChameleonAction(" + action.actions.values.joinToString { actionToString(it) } + ")"
    is ActionStub -> return "'$action' (${action.className})"
    else -> return "'$action' (${action.javaClass})"
  }
}

private val walker = StackWalker.getInstance(setOf(StackWalker.Option.RETAIN_CLASS_REFERENCE), 3)

private fun addToGroup(group: AnAction,
                       action: AnAction,
                       constraints: Constraints,
                       module: IdeaPluginDescriptor?,
                       state: ActionManagerState,
                       secondary: Boolean) {
  try {
    val actionToId: (t: AnAction) -> String? = { if (it is ActionStub) it.id else state.actionToId.get(it) }

    val actionId = actionToId(action)
    val actionGroup = group as DefaultActionGroup
    if (module != null && actionGroup.containsAction(action)) {
      reportActionError(module, "Cannot add an action twice: $actionId " +
                                "(${if (action is ActionStub) action.className else action.javaClass.name})")
      return
    }

    actionGroup
      .addAction(action, constraints, actionToId)
      .setAsSecondary(secondary)
    if (actionId != null) {
      actionToId(group)?.let { groupId ->
        state.idToDescriptor.computeIfAbsent(actionId) { ActionManagerStateActionItemDescriptor() }.addGroupMapping(groupId)
      }
    }
  }
  catch (e: IllegalArgumentException) {
    if (module == null) {
      throw e
    }
    else {
      reportActionError(module, e.message!!, e)
    }
  }
}

// executed under lock
private fun replaceStub(stub: ActionStubBase, convertedAction: AnAction, actionRegistrar: ActionRegistrar): AnAction {
  if (actionRegistrar.state.actionToId.remove(stub) == null) {
    throw IllegalStateException("No action in actionToId by stub (stub=$stub)")
  }

  updateHandlers(convertedAction)

  actionRegistrar.state.actionToId.put(convertedAction, stub.id)
  val result = (if (stub is ActionStub) stub.projectType else null)?.let { ChameleonAction(convertedAction, it) } ?: convertedAction
  actionRegistrar.putAction(stub.id, result)
  return result
}

// executed under lock
private fun registerAction(actionId: String,
                           action: AnAction,
                           pluginId: PluginId?,
                           projectType: ProjectType?,
                           actionRegistrar: ActionRegistrar,
                           oldIndex: Int = -1,
                           oldGroups: List<String>? = null) {
  val state = actionRegistrar.state
  if (state.prohibitedActionIds.contains(actionId)) {
    return
  }

  val existing = actionRegistrar.getAction(actionId)
  if (!addToMap(actionId = actionId, existing = existing, action = action, projectType = projectType, actionRegistrar)) {
    reportActionIdCollision(actionId = actionId,
                            action = action,
                            pluginId = pluginId,
                            oldAction = actionRegistrar.getAction(actionId),
                            idToDescriptor = state.idToDescriptor)
    return
  }

  val existingByAction = state.actionToId.putIfAbsent(action, actionId)
  if (existingByAction != null) {
    val module = if (pluginId == null) null else PluginManagerCore.getPluginSet().findEnabledPlugin(pluginId)
    val message = "ID '${state.actionToId.get(action)}' is already taken by action ${actionToString(action)}." +
                  " ID '$actionId' cannot be registered for the same action"
    if (module == null) {
      LOG.error(PluginException("$message $pluginId", null, pluginId))
    }
    else {
      reportActionError(module, message)
    }
    return
  }

  action.registerCustomShortcutSet(ProxyShortcutSet(actionId), null)
  val descriptor = state.idToDescriptor.computeIfAbsent(actionId) { ActionManagerStateActionItemDescriptor() }
  descriptor.index = if (oldIndex >= 0) oldIndex else state.registeredActionCount++
  if (pluginId != null) {
    descriptor.pluginId = pluginId
  }
  if (oldGroups != null) {
    descriptor.groupIds = oldGroups
  }

  actionRegistrar.actionRegistered(actionId, action)
}

private fun getAction(id: String, canReturnStub: Boolean, actionRegistrar: ActionRegistrar): AnAction? {
  var action = actionRegistrar.getAction(id)
  if (canReturnStub || action !is ActionStubBase) {
    return action
  }

  val converted = if (action is ActionStub) {
    convertStub(action)
  }
  else {
    convertGroupStub(stub = action as ActionGroupStub, actionRegistrar = actionRegistrar)
  }

  if (converted == null) {
    unregisterAction(actionId = id, actionRegistrar = actionRegistrar)
    return null
  }

  synchronized(actionRegistrar.state.lock) {
    // get under lock - maybe already replaced in parallel
    action = actionRegistrar.getAction(id)
    return replaceStub(stub = action as? ActionStubBase ?: return action, convertedAction = converted, actionRegistrar = actionRegistrar)
  }
}

// must be called under lock
private fun unregisterAction(actionId: String, actionRegistrar: ActionRegistrar, removeFromGroups: Boolean = true) {
  val actionToRemove = actionRegistrar.getAction(actionId)
  if (actionToRemove == null) {
    LOG.debug { "action with ID $actionId wasn't registered" }
    return
  }

  actionRegistrar.removeAction(actionId)

  val state = actionRegistrar.state
  state.actionToId.remove(actionToRemove)
  val parentGroupIds = state.idToDescriptor.remove(actionId)?.groupIds
  if (removeFromGroups && !parentGroupIds.isNullOrEmpty()) {
    val customActionSchema = serviceIfCreated<CustomActionsSchema>()
    for (groupId in parentGroupIds) {
      customActionSchema?.invalidateCustomizedActionGroup(groupId)
      val group = getAction(id = groupId, canReturnStub = true, actionRegistrar = actionRegistrar) as DefaultActionGroup?
      if (group == null) {
        LOG.error("Trying to remove action $actionId from non-existing group $groupId")
        continue
      }

      group.remove(actionToRemove, actionId)
      if (group is ActionGroupStub) {
        continue
      }

      // group can be used as a stub in other actions
      for (parentOfGroup in state.getParentGroupIds(groupId)) {
        val parentOfGroupAction =
          getAction(id = parentOfGroup, canReturnStub = true, actionRegistrar = actionRegistrar) as DefaultActionGroup?
        if (parentOfGroupAction == null) {
          LOG.error("Trying to remove action $actionId from non-existing group $parentOfGroup")
          continue
        }

        for (stub in parentOfGroupAction.childActionsOrStubs) {
          if (stub is ActionGroupStub && groupId == stub.id) {
            stub.remove(actionToRemove, actionId)
          }
        }
      }
    }
  }

  if (actionToRemove is ActionGroup) {
    for (item in state.idToDescriptor.values) {
      item.removeGroupMapping(actionId)
    }
  }
  updateHandlers(actionToRemove)
}

private fun replaceAction(actionId: String, newAction: AnAction, pluginId: PluginId?, actionRegistrar: ActionRegistrar): AnAction? {
  val state = actionRegistrar.state
  if (state.prohibitedActionIds.contains(actionId)) {
    return null
  }

  val oldAction = if (newAction is OverridingAction) {
    getAction(id = actionId, canReturnStub = false, actionRegistrar = actionRegistrar)
  }
  else {
    getAction(id = actionId, canReturnStub = true, actionRegistrar = actionRegistrar)
  }

  val actionItemDescriptor = state.idToDescriptor.get(actionId)
  // valid indices >= 0
  val oldIndex = actionItemDescriptor?.index ?: -1
  if (oldAction != null) {
    state.baseActions.put(actionId, oldAction)
    val isGroup = oldAction is ActionGroup
    check(isGroup == newAction is ActionGroup) {
      "cannot replace a group with an action and vice versa: $actionId"
    }

    if (actionItemDescriptor != null) {
      for (groupId in actionItemDescriptor.groupIds) {
        val group = getAction(id = groupId, canReturnStub = true, actionRegistrar = actionRegistrar) as DefaultActionGroup?
                    ?: throw IllegalStateException("Trying to replace action which has been added to a non-existing group $groupId")
        group.replaceAction(oldAction, newAction)
      }
    }
    unregisterAction(actionId = actionId, removeFromGroups = false, actionRegistrar = actionRegistrar)
  }

  registerAction(actionId = actionId,
                 action = newAction,
                 pluginId = pluginId,
                 projectType = null,
                 actionRegistrar = actionRegistrar,
                 oldIndex = oldIndex,
                 oldGroups = actionItemDescriptor?.groupIds)
  return oldAction
}

private fun registerOrReplaceActionInner(element: XmlElement,
                                         id: String,
                                         action: AnAction,
                                         plugin: IdeaPluginDescriptor,
                                         actionRegistrar: ActionRegistrar) {
  if (actionRegistrar.state.prohibitedActionIds.contains(id)) {
    return
  }

  synchronized(actionRegistrar.state.lock) {
    if (element.attributes.get(OVERRIDES_ATTR_NAME).toBoolean()) {
      val actionOrStub = getAction(id = id, canReturnStub = true, actionRegistrar = actionRegistrar)
      if (actionOrStub == null) {
        LOG.error("'$id' action group in '${plugin.name}' does not override anything")
        return
      }
      if (action is ActionGroup && actionOrStub is ActionGroup && action.isPopup != actionOrStub.isPopup) {
        LOG.info("'$id' action group in '${plugin.name}' sets isPopup=$action.isPopup")
      }

      val prev = replaceAction(actionId = id, newAction = action, pluginId = plugin.pluginId, actionRegistrar = actionRegistrar)
      if (action is DefaultActionGroup && prev is DefaultActionGroup) {
        if (element.attributes.get("keep-content").toBoolean()) {
          action.copyFromGroup(prev)
        }
      }
    }
    else {
      registerAction(actionId = id,
                     action = action,
                     pluginId = plugin.pluginId,
                     projectType = element.attributes.get(PROJECT_TYPE)?.let { ProjectType.create(it) },
                     actionRegistrar = actionRegistrar)
    }
    onActionLoadedFromXml(actionId = id, plugin = plugin)
  }
}
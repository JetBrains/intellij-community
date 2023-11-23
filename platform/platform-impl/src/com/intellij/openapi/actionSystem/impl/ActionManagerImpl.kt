// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionPopupMenuListener
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.extensions.*
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.impl.DefaultKeymap
import com.intellij.openapi.keymap.impl.KeymapImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.ProjectType
import com.intellij.openapi.util.*
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.platform.util.coroutines.childScope
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.serviceContainer.executeRegisterTaskForOldContent
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.util.ArrayUtilRt
import com.intellij.util.DefaultBundleService
import com.intellij.util.ReflectionUtil
import com.intellij.util.childScope
import com.intellij.util.concurrency.*
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.with
import com.intellij.util.containers.without
import com.intellij.util.ui.StartupUiUtil.addAwtListener
import com.intellij.util.xml.dom.XmlElement
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
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
import java.awt.event.WindowEvent
import java.util.*
import java.util.concurrent.CancellationException
import java.util.function.Function
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val DEFAULT_ACTION_GROUP_CLASS_NAME = DefaultActionGroup::class.java.name

open class ActionManagerImpl protected constructor(private val coroutineScope: CoroutineScope) : ActionManagerEx(), Disposable {
  private val lock = Any()

  private val pluginToId = HashMap<PluginId, MutableList<String>>()
  private val idToIndex = Object2IntOpenHashMap<String>().also { it.defaultReturnValue(-1) }

  @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
  @Volatile
  private var prohibitedActionIds = java.util.Set.of<String>()

  private val actionToId = HashMap<Any, String>(5_000, 0.5f)
  private val idToGroupId = HashMap<String, MutableList<String>>()
  private val notRegisteredInternalActionIds = ArrayList<String>()
  private val actionListeners = ContainerUtil.createLockFreeCopyOnWriteList<AnActionListener>()
  private val actionPopupMenuListeners = ContainerUtil.createLockFreeCopyOnWriteList<ActionPopupMenuListener>()
  private val popups = ArrayList<Any>()
  private var timer: MyTimer? = null
  private var registeredActionCount = 0

  override var lastPreformedActionId: String? = null

  override var prevPreformedActionId: String? = null

  private var lastTimeEditorWasTypedIn: Long = 0
  private val baseActions = HashMap<String, AnAction>()
  private var anonymousGroupIdCounter = 0

  private val actionPostInitRegistrar: ActionPostInitRegistrar

  init {
    val app = ApplicationManager.getApplication()
    if (!app.isUnitTestMode && !app.isHeadlessEnvironment && !app.isCommandLine) {
      ThreadingAssertions.assertBackgroundThread()
    }

    val idToAction = HashMap<String, AnAction>(5_000, 0.5f)
    val boundShortcuts = HashMap<String, String>(512, 0.5f)
    val actionPreInitRegistrar = ActionPreInitRegistrar(idToAction, boundShortcuts)
    doRegisterActions(PluginManagerCore.getPluginSet().getEnabledModules(), actionRegistrar = actionPreInitRegistrar)

    // by intention, _after_ doRegisterActions
    actionPostInitRegistrar = ActionPostInitRegistrar(idToAction = idToAction, boundShortcuts = boundShortcuts)

    EP.forEachExtensionSafe { it.customize(this) }
    DYNAMIC_EP_NAME.forEachExtensionSafe { it.registerActions(this) }

    DYNAMIC_EP_NAME.addExtensionPointListener(coroutineScope, object : ExtensionPointListener<DynamicActionConfigurationCustomizer> {
      override fun extensionAdded(extension: DynamicActionConfigurationCustomizer, pluginDescriptor: PluginDescriptor) {
        extension.registerActions(this@ActionManagerImpl)
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

  override fun getBoundActions(): Set<String> = actionPostInitRegistrar.getBoundActions()

  override fun getActionBinding(actionId: String): String? = actionPostInitRegistrar.getActionBinding(actionId)

  override fun bindShortcuts(sourceActionId: String, targetActionId: String) {
    actionPostInitRegistrar.bindShortcuts(sourceActionId = sourceActionId, targetActionId = targetActionId)
  }

  override fun unbindShortcuts(targetActionId: String) {
    actionPostInitRegistrar.unbindShortcuts(targetActionId)
  }

  internal fun registerActions(modules: Iterable<IdeaPluginDescriptorImpl>) {
    doRegisterActions(modules = modules, actionRegistrar = actionPostInitRegistrar)
  }

  private fun doRegisterActions(modules: Iterable<IdeaPluginDescriptorImpl>, actionRegistrar: ActionRegistrar) {
    val keymapToOperations = HashMap<String, MutableList<KeymapShortcutOperation>>()
    for (module in modules) {
      registerPluginActions(module = module, keymapToOperations = keymapToOperations, actionRegistrar = actionRegistrar)
      executeRegisterTaskForOldContent(module) {
        registerPluginActions(module = it, keymapToOperations = keymapToOperations, actionRegistrar = actionRegistrar)
      }
    }

    // out of ActionManager constructor
    coroutineScope.launch {
      // make sure constructor is completed
      serviceAsync<ActionManager>()

      val keymapManager = serviceAsync<KeymapManager>()
      for ((keymapName, operations) in keymapToOperations) {
        val keymap = keymapManager.getKeymap(keymapName) as KeymapImpl?
        if (keymap == null) {
          val app = ApplicationManager.getApplication()
          if (!app.isHeadlessEnvironment && !app.isCommandLine && !DefaultKeymap.isBundledKeymapHidden(keymapName)) {
            LOG.info("keymap \"$keymapName\" not found")
          }
          continue
        }

        keymap.apply(operations, actionBinding = actionRegistrar::getActionBinding, actionManager = this@ActionManagerImpl)
      }
    }
  }

  final override fun dispose() {
    timer?.let {
      it.stop()
      timer = null
    }
  }

  final override fun addTimerListener(listener: TimerListener) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    if (timer == null) {
      timer = MyTimer(coroutineScope.childScope())
    }

    val wrappedListener = if (AppExecutorUtil.propagateContextOrCancellation() && listener !is CapturingListener) {
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

      when (descriptor) {
        is ActionDescriptorAction -> {
          processActionElement(className = descriptor.className,
                               isInternal = descriptor.isInternal,
                               element = element,
                               actionRegistrar = actionRegistrar,
                               module = module,
                               bundle = bundle,
                               keymapToOperations = keymapToOperations,
                               classLoader = module.classLoader)
        }
        is ActionDescriptorGroup -> {
          processGroupElement(className = descriptor.className,
                              id = descriptor.id,
                              element = element,
                              actionRegistrar = actionRegistrar,
                              module = module,
                              bundle = bundle,
                              keymapToOperations = keymapToOperations,
                              classLoader = module.classLoader)
        }
        else -> {
          when (descriptor.name) {
            ActionDescriptorName.separator -> processSeparatorNode(parentGroup = null,
                                                                   element = element,
                                                                   module = module,
                                                                   bundle = bundle,
                                                                   actionRegistrar = actionRegistrar)
            ActionDescriptorName.reference -> processReferenceNode(element = element,
                                                                   module = module,
                                                                   bundle = bundle,
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
    return doGetAction(id = id, canReturnStub = false, actionRegistrar = actionPostInitRegistrar)
  }

  private fun doGetAction(id: String, canReturnStub: Boolean, actionRegistrar: ActionRegistrar): AnAction? {
    var action = actionRegistrar.getAction(id)
    if (canReturnStub || action !is ActionStubBase) {
      return action
    }

    val converted = if (action is ActionStub) {
      convertStub(action)
    }
    else {
      convertGroupStub(stub = action as ActionGroupStub, actionManager = this)
    }

    if (converted == null) {
      unregisterAction(actionId = id, actionRegistrar = actionRegistrar)
      return null
    }

    synchronized(lock) {
      // get under lock - maybe already replaced in parallel
      action = actionRegistrar.getAction(id)
      return replaceStub(stub = action as? ActionStubBase ?: return action, convertedAction = converted, actionRegistrar = actionRegistrar)
    }
  }

  // executed under lock
  private fun replaceStub(stub: ActionStubBase, convertedAction: AnAction, actionRegistrar: ActionRegistrar): AnAction {
    if (actionToId.remove(stub) == null) {
      throw IllegalStateException("No action in actionToId by stub (stub=$stub)")
    }

    updateHandlers(convertedAction)

    actionToId.put(convertedAction, stub.id)
    val result = (if (stub is ActionStub) stub.projectType else null)?.let { ChameleonAction(convertedAction, it) } ?: convertedAction
    actionRegistrar.putAction(stub.id, result)
    return result
  }

  override fun getId(action: AnAction): String? {
    if (action is ActionStubBase) {
      return action.id
    }
    synchronized(lock) {
      return actionToId.get(action)
    }
  }

  final override fun getActionIdList(idPrefix: String): List<String> {
    return actionPostInitRegistrar.getActionIdList(idPrefix)
  }

  @Suppress("OVERRIDE_DEPRECATION")
  final override fun getActionIds(idPrefix: String): Array<String> = ArrayUtilRt.toStringArray(getActionIdList(idPrefix))

  final override fun isGroup(actionId: String): Boolean {
    return doGetAction(id = actionId, canReturnStub = true, actionPostInitRegistrar) is ActionGroup
  }

  @Suppress("removal", "OVERRIDE_DEPRECATION")
  override fun createButtonToolbar(actionPlace: String, messageActionGroup: ActionGroup): JComponent {
    @Suppress("removal", "DEPRECATION")
    return ButtonToolbarImpl(actionPlace, messageActionGroup)
  }

  final override fun getActionOrStub(id: String): AnAction? {
    return doGetAction(id = id, canReturnStub = true, actionRegistrar = actionPostInitRegistrar)
  }

  @Experimental
  @Internal
  fun actionsOrStubs(): Sequence<AnAction> {
    return actionPostInitRegistrar.actionsOrStubs()
  }

  /**
   * @return instance of ActionGroup or ActionStub. The method never returns real subclasses of `AnAction`.
   */
  private fun processActionElement(className: String,
                                   isInternal: Boolean,
                                   element: XmlElement,
                                   actionRegistrar: ActionRegistrar,
                                   module: IdeaPluginDescriptorImpl,
                                   bundle: ResourceBundle?,
                                   keymapToOperations: MutableMap<String, MutableList<KeymapShortcutOperation>>,
                                   classLoader: ClassLoader): AnAction? {
    // read ID and register a loaded action
    val id = obtainActionId(element = element, className = className)
    if (prohibitedActionIds.contains(id)) {
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
      presentation.setText(Supplier {
        computeActionText(bundle = bundle, id = id, elementType = ACTION_ELEMENT_NAME, textValue = textValue, classLoader = classLoader)
      })
      if (bundle == null) {
        presentation.description = descriptionValue
      }
      else {
        presentation.setDescription {
          computeDescription(bundle = bundle,
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
                                                              bundle = bundle)
        SYNONYM_ELEMENT_NAME -> processSynonymNode(action = stub, element = child, module = module, bundle = bundle)
        else -> {
          reportActionError(module, "unexpected name of element \"${child.name}\"")
          return null
        }
      }
    }

    element.attributes.get(USE_SHORTCUT_OF_ATTR_NAME)?.let { shortcutOfActionId ->
      actionRegistrar.bindShortcuts(sourceActionId = shortcutOfActionId, targetActionId = id)
    }
    registerOrReplaceActionInner(element = element, id = id, action = stub, plugin = module, actionRegistrar = actionRegistrar)
    return stub
  }

  private fun registerOrReplaceActionInner(element: XmlElement,
                                           id: String,
                                           action: AnAction,
                                           plugin: IdeaPluginDescriptor,
                                           actionRegistrar: ActionRegistrar) {
    if (prohibitedActionIds.contains(id)) {
      return
    }

    synchronized(lock) {
      if (element.attributes.get(OVERRIDES_ATTR_NAME).toBoolean()) {
        val actionOrStub = doGetAction(id = id, canReturnStub = true, actionRegistrar = actionRegistrar)
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
        doRegisterAction(actionId = id,
                         action = action,
                         pluginId = plugin.pluginId,
                         projectType = element.attributes.get(PROJECT_TYPE)?.let { ProjectType.create(it) },
                         actionRegistrar = actionRegistrar)
      }
      onActionLoadedFromXml(actionId = id, plugin = plugin)
    }
  }

  private fun processGroupElement(className: String?,
                                  id: String?,
                                  element: XmlElement,
                                  module: IdeaPluginDescriptorImpl,
                                  bundle: ResourceBundle?,
                                  keymapToOperations: MutableMap<String, MutableList<KeymapShortcutOperation>>,
                                  actionRegistrar: ActionRegistrar,
                                  classLoader: ClassLoader): AnAction? {
    try {
      if (id != null && prohibitedActionIds.contains(id)) {
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
                                       bundle = bundle,
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
                                                bundle = bundle,
                                                actionRegistrar = actionRegistrar,
                                                keymapToOperations = keymapToOperations,
                                                classLoader = classLoader)
              if (action != null) {
                addToGroupInner(group = group,
                                action = action,
                                constraints = Constraints.LAST,
                                module = module,
                                secondary = isSecondary(child))
              }
            }
          }
          SEPARATOR_ELEMENT_NAME -> {
            processSeparatorNode(parentGroup = group as DefaultActionGroup,
                                 element = child,
                                 module = module,
                                 bundle = bundle,
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
                                               bundle = bundle,
                                               keymapToOperations = keymapToOperations,
                                               actionRegistrar = actionRegistrar,
                                               classLoader = classLoader)
              if (action != null) {
                addToGroupInner(group = group, action = action, constraints = Constraints.LAST, module = module, secondary = false)
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
              addToGroupInner(group = group,
                              action = action,
                              constraints = Constraints.LAST,
                              module = module,
                              secondary = isSecondary(child))
            }
          }
          OVERRIDE_TEXT_ELEMENT_NAME -> processOverrideTextNode(action = group, id = id, element = child, module = module, bundle = bundle)
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
                                   bundle: ResourceBundle?,
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
        processSynonymNode(action = action, element = child, module = module, bundle = bundle)
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
    val id = if (action is ActionStub) action.id else actionToId.get(action)!!
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

    addToGroupInner(group = parentGroup,
                    action = action,
                    constraints = Constraints(anchor, relativeToActionId),
                    module = module,
                    secondary = secondary)
  }

  private fun addToGroupInner(group: AnAction,
                              action: AnAction,
                              constraints: Constraints,
                              module: IdeaPluginDescriptor?,
                              secondary: Boolean) {
    try {
      val actionId = if (action is ActionStub) action.id else actionToId.get(action)
      val actionGroup = group as DefaultActionGroup
      if (module != null && actionGroup.containsAction(action)) {
        reportActionError(module, "Cannot add an action twice: $actionId " +
                                  "(${if (action is ActionStub) action.className else action.javaClass.name})")
        return
      }

      actionGroup
        .addAction(action, constraints) { if (it is ActionStub) it.id else actionToId.get(it) }
        .setAsSecondary(secondary)
      if (actionId != null) {
        actionToId.get(group)?.let { groupId ->
          idToGroupId.computeIfAbsent(actionId) { mutableListOf() }.add(groupId)
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

  fun addToGroup(group: DefaultActionGroup, action: AnAction, constraints: Constraints) {
    addToGroupInner(group = group, action = action, constraints = constraints, module = null, secondary = false)
  }

  private fun getParentGroup(groupId: String?,
                             actionName: String?,
                             module: IdeaPluginDescriptor,
                             actionRegistrar: ActionRegistrar): DefaultActionGroup? {
    if (groupId.isNullOrEmpty()) {
      reportActionError(module, "$actionName: attribute \"group-id\" should be defined")
      return null
    }

    val parentGroup = doGetAction(id = groupId, canReturnStub = true, actionRegistrar = actionRegistrar)
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
   * @param element     XML element which represent separator.
  </add-to-group> */
  @Suppress("HardCodedStringLiteral")
  private fun processSeparatorNode(parentGroup: DefaultActionGroup?,
                                   element: XmlElement,
                                   module: IdeaPluginDescriptor,
                                   bundle: ResourceBundle?,
                                   actionRegistrar: ActionRegistrar) {
    val text = element.attributes.get(TEXT_ATTR_NAME)
    val key = element.attributes.get(KEY_ATTR_NAME)
    val separator = when {
      text != null -> Separator(text)
      key != null -> createSeparator(bundle, key)
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

    val action = doGetAction(id = id, canReturnStub = false, actionRegistrar = actionRegistrar)
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

    if (prohibitedActionIds.contains(ref)) {
      return null
    }

    val action = doGetAction(id = ref, canReturnStub = true, actionRegistrar = actionRegistrar)
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
            idToGroupId.get(actionId)?.remove(groupId)
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
      val baseAction = baseActions.get(id)
      if (baseAction != null) {
        replaceAction(id, baseAction)
        baseActions.remove(id)
        return
      }
    }
    unregisterAction(id)
  }

  override fun registerAction(actionId: String, action: AnAction, pluginId: PluginId?) {
    synchronized(lock) {
      doRegisterAction(actionId = actionId,
                       action = action,
                       pluginId = pluginId,
                       projectType = null,
                       actionRegistrar = actionPostInitRegistrar)
    }
  }

  // executed under lock
  private fun doRegisterAction(actionId: String,
                               action: AnAction,
                               pluginId: PluginId?,
                               projectType: ProjectType?,
                               actionRegistrar: ActionRegistrar) {
    if (prohibitedActionIds.contains(actionId)) {
      return
    }

    val existing = actionRegistrar.getAction(actionId)
    if (!addToMap(actionId = actionId, existing = existing, action = action, projectType = projectType, actionRegistrar)) {
      reportActionIdCollision(actionId = actionId,
                              action = action,
                              pluginId = pluginId,
                              oldAction = actionRegistrar.getAction(actionId),
                              pluginToId = pluginToId)
      return
    }

    val existingByAction = actionToId.putIfAbsent(action, actionId)
    if (existingByAction != null) {
      val module = if (pluginId == null) null else PluginManagerCore.getPluginSet().findEnabledPlugin(pluginId)
      val message = "ID '${actionToId.get(action)}' is already taken by action ${actionToString(action)}." +
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
    idToIndex.put(actionId, registeredActionCount++)
    if (pluginId != null) {
      pluginToId.computeIfAbsent(pluginId) { mutableListOf() }.add(actionId)
    }

    notifyCustomActionsSchema(actionId)
    if (actionRegistrar.isPostInit) {
      updateHandlers(action)
    }
  }

  override fun registerAction(actionId: String, action: AnAction) {
    synchronized(lock) {
      doRegisterAction(actionId = actionId, action = action, pluginId = null, projectType = null, actionRegistrar = actionPostInitRegistrar)
    }
  }

  override fun unregisterAction(actionId: String) {
    synchronized(lock) {
      unregisterAction(actionId = actionId, actionRegistrar = actionPostInitRegistrar)
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

    actionToId.remove(actionToRemove)
    idToIndex.removeInt(actionId)
    for (value in pluginToId.values) {
      value.remove(actionId)
    }

    if (removeFromGroups) {
      val customActionSchema = serviceIfCreated<CustomActionsSchema>()
      for (groupId in (idToGroupId.get(actionId) ?: emptyList())) {
        customActionSchema?.invalidateCustomizedActionGroup(groupId)
        val group = doGetAction(id = groupId, canReturnStub = true, actionRegistrar = actionRegistrar) as DefaultActionGroup?
        if (group == null) {
          LOG.error("Trying to remove action $actionId from non-existing group $groupId")
          continue
        }

        group.remove(actionToRemove, actionId)
        if (group !is ActionGroupStub) {
          // group can be used as a stub in other actions
          for (parentOfGroup in (idToGroupId.get(groupId) ?: emptyList())) {
            val parentOfGroupAction = doGetAction(id = parentOfGroup,
                                                  canReturnStub = true,
                                                  actionRegistrar = actionRegistrar) as DefaultActionGroup?
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
    }

    if (actionToRemove is ActionGroup) {
      for (value in idToGroupId.values) {
        value.remove(actionId)
      }
    }
    updateHandlers(actionToRemove)
  }

  /**
   * Unregisters already registered action and prevented the action from being registered in the future.
   * Should be used only in IDE configuration
   */
  @Internal
  fun prohibitAction(actionId: String) {
    synchronized(lock) {
      prohibitedActionIds = HashSet(prohibitedActionIds).let {
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
    synchronized(lock) {
      prohibitedActionIds = java.util.Set.of()
    }
  }

  override val registrationOrderComparator: Comparator<String>
    get() = Comparator.comparingInt { key -> idToIndex.getInt(key) }

  override fun getPluginActions(pluginId: PluginId): Array<String> = ArrayUtilRt.toStringArray(pluginToId.get(pluginId))

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

  override fun addActionPopupMenuListener(listener: ActionPopupMenuListener, parentDisposable: Disposable) {
    actionPopupMenuListeners.add(listener)
    Disposer.register(parentDisposable) { actionPopupMenuListeners.remove(listener) }
  }

  override fun replaceAction(actionId: String, newAction: AnAction) {
    val callerClass = walker.callerClass
    val plugin = if (callerClass == null) null else PluginManager.getPluginByClass(callerClass)
    synchronized(lock) {
      replaceAction(actionId = actionId, newAction = newAction, pluginId = plugin?.pluginId, actionRegistrar = actionPostInitRegistrar)
    }
  }

  private fun replaceAction(actionId: String, newAction: AnAction, pluginId: PluginId?, actionRegistrar: ActionRegistrar): AnAction? {
    if (prohibitedActionIds.contains(actionId)) {
      return null
    }

    val oldAction = if (newAction is OverridingAction) {
      doGetAction(id = actionId, canReturnStub = false, actionRegistrar = actionRegistrar)
    }
    else {
      doGetAction(id = actionId, canReturnStub = true, actionRegistrar = actionRegistrar)
    }
    // valid indices >= 0
    val oldIndex = idToIndex.getInt(actionId)
    if (oldAction != null) {
      baseActions.put(actionId, oldAction)
      val isGroup = oldAction is ActionGroup
      check(isGroup == newAction is ActionGroup) {
        "cannot replace a group with an action and vice versa: $actionId"
      }

      for (groupId in (idToGroupId.get(actionId) ?: emptyList())) {
        val group = doGetAction(id = groupId, canReturnStub = true, actionRegistrar = actionRegistrar) as DefaultActionGroup?
                    ?: throw IllegalStateException("Trying to replace action which has been added to a non-existing group $groupId")
        group.replaceAction(oldAction, newAction)
      }
      unregisterAction(actionId = actionId, removeFromGroups = false, actionRegistrar = actionRegistrar)
    }

    doRegisterAction(actionId = actionId,
                     action = newAction,
                     pluginId = pluginId,
                     projectType = null,
                     actionRegistrar = actionRegistrar)
    if (oldIndex >= 0) {
      idToIndex.put(actionId, oldIndex)
    }
    return oldAction
  }

  /**
   * Returns the action overridden by the specified overriding action (with overrides="true" in plugin.xml).
   */
  fun getBaseAction(overridingAction: OverridingAction): AnAction? {
    val id = getId(overridingAction as AnAction) ?: return null
    return baseActions.get(id)
  }

  fun getParentGroupIds(actionId: String?): Collection<String?> = idToGroupId.get(actionId) ?: emptyList()

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
    publisher().beforeEditorTyping(c, dataContext)
  }

  override fun fireAfterEditorTyping(c: Char, dataContext: DataContext) {
    for (listener in actionListeners) {
      listener.afterEditorTyping(c, dataContext)
    }
    publisher().afterEditorTyping(c, dataContext)
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
          doGetAction(id = it, canReturnStub = false, actionRegistrar = actionPostInitRegistrar)
        }
    }
  }

  @TestOnly
  fun preloadActions() {
    for (id in actionPostInitRegistrar.ids) {
      doGetAction(id = id, canReturnStub = false, actionRegistrar = actionPostInitRegistrar)
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
    val doRunnable = {
      tryToExecuteNow(action = action,
                      inputEvent = inputEvent,
                      contextComponent = contextComponent,
                      place = place,
                      result = result,
                      actionManager = this)
    }
    if (now) {
      doRunnable()
    }
    else {
      SwingUtilities.invokeLater(doRunnable)
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

private fun tryToExecuteNow(action: AnAction,
                            actionManager: ActionManager,
                            inputEvent: InputEvent?,
                            contextComponent: Component?,
                            place: String?,
                            result: ActionCallback) {
  val presentation = action.templatePresentation.clone()
  IdeFocusManager.findInstanceByContext(getContextBy(contextComponent)).doWhenFocusSettlesDown(
    {
      (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity {
        val context = getContextBy(contextComponent)
        val event = AnActionEvent(
          /* inputEvent = */ inputEvent,
          /* dataContext = */ context,
          /* place = */ place ?: ActionPlaces.UNKNOWN,
          /* presentation = */ presentation,
          /* actionManager = */ actionManager,
          /* modifiers = */ inputEvent?.modifiersEx ?: 0
        )
        ActionUtil.lastUpdateAndCheckDumb(action, event, false)
        if (!event.presentation.isEnabled) {
          result.setRejected()
          return@performUserActivity
        }
        addAwtListener(
          { event1 ->
            if (event1.id == WindowEvent.WINDOW_OPENED || event1.id == WindowEvent.WINDOW_ACTIVATED) {
              if (!result.isProcessed) {
                val we = event1 as WindowEvent
                IdeFocusManager.findInstanceByComponent(we.window).doWhenFocusSettlesDown(
                  result.createSetDoneRunnable(), ModalityState.defaultModalityState())
              }
            }
          }, AWTEvent.WINDOW_EVENT_MASK, result)
        try {
          ActionUtil.performActionDumbAwareWithCallbacks(action, event)
        }
        finally {
          result.setDone()
        }
      }
    }, ModalityState.defaultModalityState())
}

private class CapturingListener(@JvmField val timerListener: TimerListener) : TimerListener by timerListener {
  val childContext: ChildContext = createChildContext()

  override fun run() {
    installThreadContext(childContext.context).use {
      // this is periodic runnable that is invoked on timer; it should not complete a parent job
      childContext.runAsCoroutine(completeOnFinish = false, timerListener::run)
    }
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

private val EP = ExtensionPointName<ActionConfigurationCustomizer>("com.intellij.actionConfigurationCustomizer")
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
private const val KEYMAP_ATTR_NAME = "keymap"
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

private fun convertGroupStub(stub: ActionGroupStub, actionManager: ActionManager): ActionGroup? {
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
  stub.initGroup(group, actionManager::getId)
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
private fun computeDescription(bundle: ResourceBundle?,
                               id: String,
                               elementType: String,
                               descriptionValue: String?,
                               classLoader: ClassLoader): @NlsActions.ActionDescription String? {
  var effectiveBundle = bundle
  if (effectiveBundle != null && DefaultBundleService.isDefaultBundle()) {
    effectiveBundle = DynamicBundle.getResourceBundle(classLoader, effectiveBundle.baseBundleName)
  }
  return AbstractBundle.messageOrDefault(effectiveBundle, "$elementType.$id.$DESCRIPTION", descriptionValue ?: "")
}

@Suppress("HardCodedStringLiteral")
private fun computeActionText(bundle: ResourceBundle?,
                              id: String,
                              elementType: String,
                              textValue: String?,
                              classLoader: ClassLoader): @NlsActions.ActionText String? {
  var effectiveBundle = bundle
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

private fun processMouseShortcutNode(element: XmlElement,
                                     actionId: String,
                                     module: IdeaPluginDescriptor,
                                     keymapToOperations: MutableMap<String, MutableList<KeymapShortcutOperation>>) {
  val keystrokeString = element.attributes.get("keystroke")
  if (keystrokeString.isNullOrBlank()) {
    reportActionError(module, "\"keystroke\" attribute must be specified for action with id=$actionId")
    return
  }

  val shortcut = try {
    KeymapUtil.parseMouseShortcut(keystrokeString)
  }
  catch (_: Exception) {
    reportActionError(module, "\"keystroke\" attribute has invalid value for action with id=$actionId")
    return
  }

  val keymapName = element.attributes.get(KEYMAP_ATTR_NAME)
  if (keymapName.isNullOrEmpty()) {
    reportActionError(module, "attribute \"keymap\" should be defined")
    return
  }

  processRemoveAndReplace(element = element,
                          actionId = actionId,
                          keymap = keymapName,
                          shortcut = shortcut,
                          keymapToOperations = keymapToOperations)
}

private fun reportActionError(module: PluginDescriptor, message: String, cause: Throwable? = null) {
  LOG.error(PluginException("$message (module=$module)", cause, module.pluginId))
}

private fun getPluginInfo(id: PluginId?): String {
  val plugin = (if (id == null) null else PluginManagerCore.getPlugin(id)) ?: return ""
  return " (Plugin: ${plugin.name ?: id!!.idString})"
}

private fun getContextBy(contextComponent: Component?): DataContext {
  val dataManager = DataManager.getInstance()
  @Suppress("DEPRECATION")
  return if (contextComponent == null) dataManager.dataContext else dataManager.getDataContext(contextComponent)
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
                                    module: IdeaPluginDescriptor, bundle: ResourceBundle?) {
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
    val text = element.attributes.get(TEXT_ATTR_NAME)
    if (text.isNullOrEmpty() && bundle != null) {
      val prefix = if (action is ActionGroup) "group" else "action"
      val key = "$prefix.$id.$place.text"
      action.addTextOverride(place) { BundleBase.message(bundle, key) }
    }
    else {
      action.addTextOverride(place) { text }
    }
  }
}

private fun processSynonymNode(action: AnAction, element: XmlElement, module: IdeaPluginDescriptor, bundle: ResourceBundle?) {
  val text = element.attributes.get(TEXT_ATTR_NAME)
  if (!text.isNullOrEmpty()) {
    action.addSynonym { text }
  }
  else {
    val key = element.attributes.get(KEY_ATTR_NAME)
    if (key != null && bundle != null) {
      action.addSynonym { BundleBase.message(bundle, key) }
    }
    else {
      reportActionError(module, "Can't process synonym: neither text nor resource bundle key is specified")
    }
  }
}

private fun createSeparator(bundle: ResourceBundle?, key: String): Separator {
  val text = if (bundle == null) null else AbstractBundle.messageOrNull(bundle, key)
  return if (text == null) Separator.getInstance() else Separator(text)
}

private fun processKeyboardShortcutNode(element: XmlElement,
                                        actionId: String,
                                        module: PluginDescriptor,
                                        keymapToOperations: MutableMap<String, MutableList<KeymapShortcutOperation>>) {
  val firstStrokeString = element.attributes.get("first-keystroke")
  if (firstStrokeString == null) {
    reportActionError(module, "\"first-keystroke\" attribute must be specified for action with id=$actionId")
    return
  }

  val firstKeyStroke = ActionManagerEx.getKeyStroke(firstStrokeString)
  if (firstKeyStroke == null) {
    reportActionError(module = module, message = "\"first-keystroke\" attribute has invalid value for action with id=$actionId")
    return
  }

  var secondKeyStroke: KeyStroke? = null
  val secondStrokeString = element.attributes.get("second-keystroke")
  if (secondStrokeString != null) {
    secondKeyStroke = ActionManagerEx.getKeyStroke(secondStrokeString)
    if (secondKeyStroke == null) {
      reportActionError(module = module, message = "\"second-keystroke\" attribute has invalid value for action with id=$actionId")
      return
    }
  }

  val keymapName = element.attributes.get(KEYMAP_ATTR_NAME)
  if (keymapName.isNullOrBlank()) {
    reportActionError(module = module, message = "attribute \"keymap\" should be defined")
    return
  }

  processRemoveAndReplace(element = element,
                          actionId = actionId,
                          keymap = keymapName,
                          shortcut = KeyboardShortcut(firstKeyStroke, secondKeyStroke),
                          keymapToOperations = keymapToOperations)
}

private fun processRemoveAndReplace(element: XmlElement,
                                    actionId: String,
                                    keymap: String,
                                    shortcut: Shortcut,
                                    keymapToOperations: MutableMap<String, MutableList<KeymapShortcutOperation>>) {
  val operations = keymapToOperations.computeIfAbsent(keymap) { ArrayList() }
  val remove = element.attributes.get("remove").toBoolean()
  if (remove) {
    operations.add(RemoveShortcutOperation(actionId, shortcut))
  }

  val replace = element.attributes.get("replace-all").toBoolean()
  if (replace) {
    operations.add(RemoveAllShortcutsOperation(actionId))
  }
  if (!remove) {
    operations.add(AddShortcutOperation(actionId, shortcut))
  }
}

internal sealed interface KeymapShortcutOperation
internal class RemoveShortcutOperation(@JvmField val actionId: String, @JvmField val shortcut: Shortcut) : KeymapShortcutOperation
internal class RemoveAllShortcutsOperation(@JvmField val actionId: String) : KeymapShortcutOperation
internal class AddShortcutOperation(@JvmField val actionId: String, @JvmField val shortcut: Shortcut) : KeymapShortcutOperation

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

private fun notifyCustomActionsSchema(registeredID: String) {
  val schema = ApplicationManager.getApplication().serviceIfCreated<CustomActionsSchema>() ?: return
  for (url in schema.getActions()) {
    if (registeredID == url.component) {
      schema.incrementModificationStamp()
      break
    }
  }
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
                                             bundle: ResourceBundle?,
                                             id: String,
                                             classLoader: ClassLoader,
                                             iconPath: String?,
                                             module: IdeaPluginDescriptorImpl,
                                             className: String?) {
  // don't override value which was set in API with empty value from xml descriptor
  presentation.setFallbackPresentationText {
    computeActionText(bundle = bundle, id = id, elementType = GROUP_ELEMENT_NAME, textValue = textValue, classLoader = classLoader)
  }

  // description
  if (bundle == null) {
    // don't override value which was set in API with empty value from xml descriptor
    if (!description.isNullOrEmpty() || presentation.description == null) {
      presentation.description = description
    }
  }
  else {
    val descriptionSupplier = Supplier {
      computeDescription(bundle = bundle,
                         id = id,
                         elementType = GROUP_ELEMENT_NAME,
                         descriptionValue = description,
                         classLoader = classLoader)
    }
    // don't override value which was set in API with empty value from xml descriptor
    if (!descriptionSupplier.get().isNullOrEmpty() || presentation.description == null) {
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
  val isPostInit: Boolean

  fun putAction(actionId: String, action: AnAction)

  fun removeAction(actionId: String)

  fun getAction(id: String): AnAction?

  fun bindShortcuts(sourceActionId: String, targetActionId: String)

  fun unbindShortcuts(targetActionId: String)

  fun getActionBinding(actionId: String): String?
}

private class ActionPostInitRegistrar(
  @Volatile private var idToAction: Map<String, AnAction>,
  @Volatile private var boundShortcuts: Map<String, String>,
) : ActionRegistrar {
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
    idToAction = idToAction.with(actionId, action)
  }

  override fun removeAction(actionId: String) {
    idToAction = idToAction.without(actionId)
  }

  override fun getAction(id: String) = idToAction.get(id)

  fun getActionIdList(idPrefix: String): List<String> {
    return idToAction.keys.filter { it.startsWith(idPrefix) }
  }

  fun actionsOrStubs(): Sequence<AnAction> = idToAction.values.asSequence()

  fun getBoundActions(): Set<String> = boundShortcuts.keys

  override fun getActionBinding(actionId: String) = getActionBinding(actionId, boundShortcuts)

  override fun bindShortcuts(sourceActionId: String, targetActionId: String) {
    boundShortcuts = boundShortcuts.with(targetActionId, sourceActionId)
  }

  override fun unbindShortcuts(targetActionId: String) {
    boundShortcuts = boundShortcuts.without(targetActionId)
  }
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
}

private fun reportActionIdCollision(actionId: String,
                                    action: AnAction,
                                    pluginId: PluginId?,
                                    oldAction: AnAction?,
                                    pluginToId: Map<PluginId, List<String>>) {
  val oldPluginInfo = pluginToId
    .asSequence()
    .filter { it.value.contains(actionId) }
    .map { it.key }
    .map { getPluginInfo(it) }
    .joinToString(separator = ",")
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
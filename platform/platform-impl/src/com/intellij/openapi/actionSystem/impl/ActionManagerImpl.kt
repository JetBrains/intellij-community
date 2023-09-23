// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.actionSystem.impl

import com.intellij.AbstractBundle
import com.intellij.BundleBase.message
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
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.extensions.*
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.keymap.impl.DefaultKeymap.Companion.isBundledKeymapHidden
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.ProjectType
import com.intellij.openapi.util.*
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.serviceContainer.executeRegisterTaskForOldContent
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.util.ArrayUtilRt
import com.intellij.util.DefaultBundleService
import com.intellij.util.ReflectionUtil
import com.intellij.util.childScope
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.ChildContext
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.createChildContext
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.StartupUiUtil.addAwtListener
import com.intellij.util.xml.dom.XmlElement
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.awt.AWTEvent
import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.WindowEvent
import java.util.*
import java.util.concurrent.CancellationException
import java.util.function.Consumer
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
  @Volatile
  private var idToAction = persistentHashMapOf<String, AnAction>()
  private val pluginToId = HashMap<PluginId, MutableList<String>>()
  private val idToIndex = Object2IntOpenHashMap<String>()
  @Volatile
  private var prohibitedActionIds = persistentHashSetOf<String>()
  @Suppress("SSBasedInspection")
  private val actionToId = Object2ObjectOpenHashMap<Any, String>()
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

  init {
    val app = ApplicationManager.getApplication()
    if (!app.isUnitTestMode && !app.isHeadlessEnvironment && !app.isCommandLine) {
      ApplicationManager.getApplication().assertIsNonDispatchThread()
    }
    registerActions(PluginManagerCore.getPluginSet().getEnabledModules())
    EP.forEachExtensionSafe { it.customize(this) }

    DYNAMIC_EP_NAME.forEachExtensionSafe { it.registerActions(this) }
    @Suppress("LeakingThis")
    DYNAMIC_EP_NAME.addExtensionPointListener(object : ExtensionPointListener<DynamicActionConfigurationCustomizer> {
      override fun extensionAdded(extension: DynamicActionConfigurationCustomizer, pluginDescriptor: PluginDescriptor) {
        extension.registerActions(this@ActionManagerImpl)
      }

      override fun extensionRemoved(extension: DynamicActionConfigurationCustomizer, pluginDescriptor: PluginDescriptor) {
        extension.unregisterActions(this@ActionManagerImpl)
      }
    }, this)

    @Suppress("LeakingThis")
    app.extensionArea.getExtensionPoint<Any>("com.intellij.editorActionHandler").addChangeListener({
                                                                                                     synchronized(lock) {
                                                                                                       actionToId.keys.forEach(
                                                                                                         Consumer(::updateHandlers))
                                                                                                     }
                                                                                                   }, this)
  }

  internal fun registerActions(modules: Iterable<IdeaPluginDescriptorImpl>) {
    val keymapManager = KeymapManagerEx.getInstanceEx()!!
    for (module in modules) {
      registerPluginActions(module, keymapManager)
      executeRegisterTaskForOldContent(module) {
        registerPluginActions(it, keymapManager)
      }
    }
  }

  override fun dispose() {
    timer?.let {
      it.stop()
      timer = null
    }
  }

  override fun addTimerListener(listener: TimerListener) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    if (timer == null) {
      timer = MyTimer(coroutineScope.childScope())
    }
    val wrappedListener = if (AppExecutorUtil.propagateContextOrCancellation() && listener !is CapturingListener) CapturingListener(listener) else listener
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

  override fun removeTimerListener(listener: TimerListener) {
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
    return createActionToolbar(place, group, horizontal, false, true)
  }

  override fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean, decorateButtons: Boolean): ActionToolbar {
    return createActionToolbarImpl(place, group, horizontal, decorateButtons, false)
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

  override fun createActionToolbar(place: String,
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

  private fun registerPluginActions(module: IdeaPluginDescriptorImpl, keymapManager: KeymapManagerEx) {
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
                               element = element,
                               module = module,
                               bundle = bundle,
                               keymapManager = keymapManager,
                               classLoader = module.classLoader)
        }
        is ActionDescriptorGroup -> {
          processGroupElement(className = descriptor.className,
                              id = descriptor.id,
                              element = element,
                              module = module,
                              bundle = bundle,
                              keymapManager = keymapManager,
                              classLoader = module.classLoader)
        }
        else -> {
          when (descriptor.name) {
            ActionDescriptorName.separator -> processSeparatorNode(parentGroup = null, element = element, module = module, bundle = bundle)
            ActionDescriptorName.reference -> processReferenceNode(element = element, module = module, bundle = bundle)
            ActionDescriptorName.unregister -> processUnregisterNode(element = element, module = module)
            ActionDescriptorName.prohibit -> processProhibitNode(element = element, module = module)
            else -> LOG.error("${descriptor.name} is unknown")
          }
        }
      }
    }
    StartUpMeasurer.addPluginCost(module.pluginId.idString, "Actions", System.nanoTime() - startTime)
  }

  override fun getAction(id: String): AnAction? = getActionImpl(id = id, canReturnStub = false)

  private fun getActionImpl(id: String, canReturnStub: Boolean): AnAction? {
    var action = idToAction.get(id)
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
      unregisterAction(id)
      return null
    }

    synchronized(lock) {
      // get under lock - maybe already replaced in parallel
      action = idToAction.get(id)
      return replaceStub(stub = action as? ActionStubBase ?: return action, convertedAction = converted)
    }
  }

  // executed under lock
  private fun replaceStub(stub: ActionStubBase, convertedAction: AnAction): AnAction {
    if (actionToId.remove(stub) == null) {
      throw IllegalStateException("No action in actionToId by stub (stub=$stub)")
    }

    updateHandlers(convertedAction)

    actionToId.put(convertedAction, stub.id)
    val result = (if (stub is ActionStub) stub.projectType else null)?.let { ChameleonAction(convertedAction, it) } ?: convertedAction
    idToAction = idToAction.put(stub.id, result)
    return result
  }

  override fun getId(action: AnAction): String? {
    if (action is ActionStubBase) {
      return (action as ActionStubBase).id
    }
    synchronized(lock) { return actionToId.get(action) }
  }

  override fun getActionIdList(idPrefix: String): List<String> {
    return idToAction.keys.filter { it.startsWith(idPrefix) }
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun getActionIds(idPrefix: String): Array<String> = ArrayUtilRt.toStringArray(getActionIdList(idPrefix))

  override fun isGroup(actionId: String): Boolean = getActionImpl(id = actionId, canReturnStub = true) is ActionGroup

  @Suppress("removal", "OVERRIDE_DEPRECATION")
  override fun createButtonToolbar(actionPlace: String, messageActionGroup: ActionGroup): JComponent {
    @Suppress("removal", "DEPRECATION")
    return ButtonToolbarImpl(actionPlace, messageActionGroup)
  }

  override fun getActionOrStub(id: String): AnAction? = getActionImpl(id = id, canReturnStub = true)

  @Experimental
  @Internal
  fun actionsOrStubs(): Sequence<AnAction> {
    return idToAction.values.asSequence()
  }

  /**
   * @return instance of ActionGroup or ActionStub. The method never returns real subclasses of `AnAction`.
   */
  private fun processActionElement(className: String,
                                   element: XmlElement,
                                   module: IdeaPluginDescriptorImpl,
                                   bundle: ResourceBundle?,
                                   keymapManager: KeymapManager,
                                   classLoader: ClassLoader): AnAction? {
    // read ID and register a loaded action
    val id = obtainActionId(element = element, className = className)
    if (prohibitedActionIds.contains(id)) {
      return null
    }

    if (element.attributes.get(INTERNAL_ATTR_NAME).toBoolean() && !ApplicationManager.getApplication().isInternal) {
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
    for (e in element.children) {
      when (e.name) {
        ADD_TO_GROUP_ELEMENT_NAME -> processAddToGroupNode(action = stub, element = e, module = module, secondary = isSecondary(e))
        "keyboard-shortcut" -> processKeyboardShortcutNode(element = e, actionId = id, module = module, keymapManager = keymapManager)
        "mouse-shortcut" -> processMouseShortcutNode(element = e, actionId = id, module = module, keymapManager = keymapManager)
        "abbreviation" -> processAbbreviationNode(e = e, id = id)
        OVERRIDE_TEXT_ELEMENT_NAME -> processOverrideTextNode(action = stub, id = stub.id, element = e, module = module, bundle = bundle)
        SYNONYM_ELEMENT_NAME -> processSynonymNode(action = stub, element = e, module = module, bundle = bundle)
        else -> {
          reportActionError(module, "unexpected name of element \"" + e.name + "\"")
          return null
        }
      }
    }

    val shortcutOfActionId = element.attributes.get(USE_SHORTCUT_OF_ATTR_NAME)
    if (shortcutOfActionId != null) {
      keymapManager.bindShortcuts(shortcutOfActionId, id)
    }
    registerOrReplaceActionInner(element = element, id = id, action = stub, plugin = module)
    return stub
  }

  private fun registerOrReplaceActionInner(element: XmlElement,
                                           id: String,
                                           action: AnAction,
                                           plugin: IdeaPluginDescriptor) {
    if (prohibitedActionIds.contains(id)) {
      return
    }

    synchronized(lock) {
      if (element.attributes.get(OVERRIDES_ATTR_NAME).toBoolean()) {
        val actionOrStub = getActionOrStub(id)
        if (actionOrStub == null) {
          LOG.error("'$id' action group in '${plugin.name}' does not override anything")
          return
        }
        if (action is ActionGroup && actionOrStub is ActionGroup &&
            action.isPopup != actionOrStub.isPopup) {
          LOG.info("'$id' action group in '${plugin.name}' sets isPopup=$action.isPopup")
        }

        val prev = replaceAction(actionId = id, newAction = action, pluginId = plugin.pluginId)
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
                       projectType = element.attributes.get(PROJECT_TYPE)?.let { ProjectType.create(it) })
      }
      onActionLoadedFromXml(action = action, actionId = id, plugin = plugin)
    }
  }

  private fun processGroupElement(className: String?,
                                  id: String?,
                                  element: XmlElement,
                                  module: IdeaPluginDescriptorImpl,
                                  bundle: ResourceBundle?,
                                  keymapManager: KeymapManagerEx,
                                  classLoader: ClassLoader): AnAction? {
    try {
      if (prohibitedActionIds.contains(id)) {
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

      registerOrReplaceActionInner(element = element, id = id, action = group, plugin = module)

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
        keymapManager.bindShortcuts(shortcutOfActionId, id)
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
                                                element = child,
                                                module = module,
                                                bundle = bundle,
                                                keymapManager = keymapManager,
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
            processSeparatorNode(parentGroup = group as DefaultActionGroup, element = child, module = module, bundle = bundle)
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
                                               keymapManager = keymapManager,
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
                                  secondary = isSecondary(child))
          }
          REFERENCE_ELEMENT_NAME -> {
            val action = processReferenceElement(element = child, module = module)
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

  private fun processReferenceNode(element: XmlElement, module: IdeaPluginDescriptor, bundle: ResourceBundle?) {
    val action = processReferenceElement(element, module) ?: return
    for (child in element.children) {
      if (ADD_TO_GROUP_ELEMENT_NAME == child.name) {
        processAddToGroupNode(action = action, element = child, module = module, secondary = isSecondary(child))
      }
      else if (SYNONYM_ELEMENT_NAME == child.name) {
        processSynonymNode(action = action, element = child, module = module, bundle = bundle)
      }
    }
  }

  /**
   * @param element description of a link
   */
  private fun processAddToGroupNode(action: AnAction, element: XmlElement, module: IdeaPluginDescriptor, secondary: Boolean) {
    val name = if (action is ActionStub) action.className else action.javaClass.name
    val id = if (action is ActionStub) action.id else actionToId.get(action)!!
    val actionName = "$name ($id)"

    // parent group
    val parentGroup = getParentGroup(groupId = element.attributes.get(GROUP_ID_ATTR_NAME), actionName = actionName, module = module)
                      ?: return

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

      actionGroup.addAction(action, constraints, this).setAsSecondary(secondary)
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

  fun getParentGroup(groupId: String?, actionName: String?, module: IdeaPluginDescriptor): DefaultActionGroup? {
    if (groupId.isNullOrEmpty()) {
      reportActionError(module, "$actionName: attribute \"group-id\" should be defined")
      return null
    }

    val parentGroup = getActionImpl(id = groupId, canReturnStub = true)
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
                                   bundle: ResourceBundle?) {
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
        processAddToGroupNode(action = separator, element = child, module = module, secondary = isSecondary(child))
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

  private fun processUnregisterNode(element: XmlElement, module: IdeaPluginDescriptor) {
    val id = element.attributes.get(ID_ATTR_NAME)
    if (id == null) {
      reportActionError(module, "'id' attribute is required for 'unregister' elements")
      return
    }

    val action = getAction(id)
    if (action == null) {
      reportActionError(module, "Trying to unregister non-existing action $id")
      return
    }

    AbbreviationManager.getInstance().removeAllAbbreviations(id)
    unregisterAction(id)
  }

  private fun processReferenceElement(element: XmlElement, module: IdeaPluginDescriptor): AnAction? {
    val ref = getReferenceActionId(element)
    if (ref.isNullOrEmpty()) {
      reportActionError(module, "ID of reference element should be defined", null)
      return null
    }

    if (prohibitedActionIds.contains(ref)) {
      return null
    }

    val action = getActionImpl(ref, true)
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
          val action = processReferenceElement(element, module) ?: return
          val actionId = getReferenceActionId(element)
          for ((name, attributes) in element.children) {
            if (name != ADD_TO_GROUP_ELEMENT_NAME) {
              continue
            }

            val groupId = attributes.get(GROUP_ID_ATTR_NAME)
            val parentGroup = getParentGroup(groupId = groupId, actionName = actionId, module = module) ?: return
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
      registerAction(actionId = actionId, action = action, pluginId = pluginId, projectType = null)
    }
  }

  // executed under lock
  private fun registerAction(actionId: String, action: AnAction, pluginId: PluginId?, projectType: ProjectType?) {
    if (prohibitedActionIds.contains(actionId)) {
      return
    }

    if (addToMap(actionId = actionId, action = action, projectType = projectType) == null) {
      reportActionIdCollision(actionId = actionId, action = action, pluginId = pluginId)
      return
    }

    if (actionToId.containsKey(action)) {
      val module = if (pluginId == null) null else PluginManagerCore.getPluginSet().findEnabledPlugin(pluginId)
      val message = "ID '${actionToId.get(action)}' is already taken by action '$action' (${action.javaClass})." +
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
    actionToId.put(action, actionId)
    if (pluginId != null) {
      pluginToId.computeIfAbsent(pluginId) { mutableListOf() }.add(actionId)
    }
    notifyCustomActionsSchema(actionId)
    updateHandlers(action)
  }

  // executed under lock
  private fun addToMap(actionId: String, action: AnAction, projectType: ProjectType?): AnAction? {
    val existing = idToAction.get(actionId)
    val chameleonAction: ChameleonAction
    if (existing is ChameleonAction) {
      chameleonAction = existing
    }
    else if (existing != null) {
      chameleonAction = ChameleonAction(existing, projectType)
      idToAction = idToAction.put(actionId, chameleonAction)
    }
    else {
      val result = projectType?.let { ChameleonAction(action, it) } ?: action
      idToAction = idToAction.put(actionId, result)
      return result
    }

    return chameleonAction.addAction(action, projectType)
  }

  private fun reportActionIdCollision(actionId: String, action: AnAction, pluginId: PluginId?) {
    val oldPluginInfo = pluginToId
      .asSequence()
      .filter { it.value.contains(actionId) }
      .map { it.key }
      .map { getPluginInfo(it) }
      .joinToString(separator = ",")
    val oldAction = idToAction.get(actionId)
    val message = "ID '$actionId' is already taken by action '$oldAction' (${oldAction!!.javaClass}) $oldPluginInfo. " +
                  "Action '$action' (${action.javaClass}) cannot use the same ID $pluginId"
    if (pluginId == null) {
      LOG.error(message)
    }
    else {
      LOG.error(PluginException(message, null, pluginId))
    }
  }

  override fun registerAction(actionId: String, action: AnAction) {
    synchronized(lock) {
      registerAction(actionId = actionId, action = action, pluginId = null, projectType = null)
    }
  }

  override fun unregisterAction(actionId: String) {
    unregisterAction(actionId = actionId, removeFromGroups = true)
  }

  private fun unregisterAction(actionId: String, removeFromGroups: Boolean) {
    synchronized(lock) {
      val actionToRemove = idToAction.get(actionId)
      if (actionToRemove == null) {
        LOG.debug { "action with ID $actionId wasn't registered" }
        return
      }

      idToAction = idToAction.remove(actionId)

      actionToId.remove(actionToRemove)
      idToIndex.removeInt(actionId)
      for (value in pluginToId.values) {
        value.remove(actionId)
      }

      if (removeFromGroups) {
        val customActionSchema = ApplicationManager.getApplication().serviceIfCreated<CustomActionsSchema>()
        for (groupId in (idToGroupId.get(actionId) ?: emptyList())) {
          customActionSchema?.invalidateCustomizedActionGroup(groupId)
          val group = getActionOrStub(groupId) as DefaultActionGroup?
          if (group == null) {
            LOG.error("Trying to remove action $actionId from non-existing group $groupId")
            continue
          }

          group.remove(actionToRemove, actionId)
          if (group !is ActionGroupStub) {
            // group can be used as a stub in other actions
            for (parentOfGroup in (idToGroupId.get(groupId) ?: emptyList())) {
              val parentOfGroupAction = getActionOrStub(parentOfGroup) as DefaultActionGroup?
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
  }

  /**
   * Unregisters already registered action and prevented the action from being registered in the future.
   * Should be used only in IDE configuration
   */
  @Internal
  fun prohibitAction(actionId: String) {
    synchronized(lock) {
      prohibitedActionIds = prohibitedActionIds.add(actionId)
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
      prohibitedActionIds  = prohibitedActionIds.clear()
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
    val callerClass = ReflectionUtil.getGrandCallerClass()
    val plugin = if (callerClass == null) null else PluginManager.getPluginByClass(callerClass)
    replaceAction(actionId = actionId, newAction = newAction, pluginId = plugin?.pluginId)
  }

  private fun replaceAction(actionId: String, newAction: AnAction, pluginId: PluginId?): AnAction? {
    if (prohibitedActionIds.contains(actionId)) {
      return null
    }

    val oldAction = if (newAction is OverridingAction) getAction(actionId) else getActionOrStub(actionId)
    // valid indices >= 0
    val oldIndex = idToIndex.getOrDefault<Any, Int>(actionId, -1)
    if (oldAction != null) {
      baseActions.put(actionId, oldAction)
      val isGroup = oldAction is ActionGroup
      check(isGroup == newAction is ActionGroup) {
        "cannot replace a group with an action and vice versa: $actionId"
      }

      for (groupId in (idToGroupId.get(actionId) ?: emptyList())) {
        val group = getActionOrStub(groupId) as DefaultActionGroup?
                    ?: throw IllegalStateException("Trying to replace action which has been added to a non-existing group $groupId")
        group.replaceAction(oldAction, newAction)
      }
      unregisterAction(actionId = actionId, removeFromGroups = false)
    }
    registerAction(actionId = actionId, action = newAction, pluginId = pluginId)
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

  override fun getKeyboardShortcut(actionId: String): KeyboardShortcut? {
    val action = getInstance().getAction(actionId) ?: return null
    val shortcuts = action.shortcutSet.shortcuts
    for (shortcut in shortcuts) {
      // Shortcut can be a MouseShortcut here.
      // For example, `IdeaVIM` often assigns them
      if (shortcut is KeyboardShortcut) {
        if (shortcut.secondKeyStroke == null) {
          return shortcut
        }
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
    get() = idToAction.keys

  fun preloadActions() {
    for (id in idToAction.keys) {
      getActionImpl(id = id, canReturnStub = false)
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
      tryToExecuteNow(action = action, inputEvent = inputEvent, contextComponent = contextComponent, place = place, result = result)
    }
    if (now) {
      doRunnable()
    }
    else {
      SwingUtilities.invokeLater(doRunnable)
    }
    return result
  }

  private fun tryToExecuteNow(action: AnAction,
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
            inputEvent, context,
            place ?: ActionPlaces.UNKNOWN,
            presentation, this,
            inputEvent?.modifiersEx ?: 0
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

      withClientId(clientId).use {
        for (listener in listeners) {
          runListenerAction(listener)
        }
      }
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
    val icon = loadIcon(module = stub.plugin, iconPath = iconPath, requestor = anAction.javaClass.name)
    anAction.templatePresentation.icon = icon
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
  stub.initGroup(group, actionManager)
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

private fun processMouseShortcutNode(element: XmlElement, actionId: String, module: IdeaPluginDescriptor, keymapManager: KeymapManager) {
  val keystrokeString = element.attributes.get("keystroke")
  if (keystrokeString.isNullOrBlank()) {
    reportActionError(module, "\"keystroke\" attribute must be specified for action with id=$actionId")
    return
  }

  val shortcut = try {
    KeymapUtil.parseMouseShortcut(keystrokeString)
  }
  catch (ex: Exception) {
    reportActionError(module, "\"keystroke\" attribute has invalid value for action with id=$actionId")
    return
  }

  val keymapName = element.attributes.get(KEYMAP_ATTR_NAME)
  if (keymapName.isNullOrEmpty()) {
    reportActionError(module, "attribute \"keymap\" should be defined")
    return
  }

  val keymap = keymapManager.getKeymap(keymapName)
  if (keymap == null) {
    reportKeymapNotFound(module, keymapName)
    return
  }

  processRemoveAndReplace(element = element, actionId = actionId, keymap = keymap, shortcut = shortcut)
}

private fun reportActionError(module: PluginDescriptor, message: String, cause: Throwable? = null) {
  LOG.error(PluginException("$message (module=$module)", cause, module.pluginId))
}

private fun reportKeymapNotFound(module: PluginDescriptor, keymapName: String) {
  val app = ApplicationManager.getApplication()
  if (!app.isHeadlessEnvironment && !app.isCommandLine && !isBundledKeymapHidden(keymapName)) {
    LOG.info("keymap \"$keymapName\" not found $module")
  }
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
      action.addTextOverride(place) { message(bundle, key) }
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
      action.addSynonym { message(bundle, key) }
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

private fun processKeyboardShortcutNode(element: XmlElement, actionId: String, module: PluginDescriptor, keymapManager: KeymapManager) {
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

  val keymap = keymapManager.getKeymap(keymapName)
  if (keymap == null) {
    reportKeymapNotFound(module, keymapName)
    return
  }

  processRemoveAndReplace(element = element,
                          actionId = actionId,
                          keymap = keymap,
                          shortcut = KeyboardShortcut(firstKeyStroke, secondKeyStroke))
}

private fun processRemoveAndReplace(element: XmlElement, actionId: String, keymap: Keymap, shortcut: Shortcut) {
  val remove = element.attributes.get("remove").toBoolean()
  val replace = element.attributes.get("replace-all").toBoolean()
  if (remove) {
    keymap.removeShortcut(actionId, shortcut)
  }
  if (replace) {
    keymap.removeAllActionShortcuts(actionId)
  }
  if (!remove) {
    keymap.addShortcut(actionId, shortcut)
  }
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
    presentation.icon = loadIcon(module = module, iconPath = iconPath, requestor = className)
  }
}

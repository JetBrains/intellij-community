// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet",
               "ReplacePutWithAssignment",
               "ReplaceJavaStaticMethodWithKotlinAnalog",
               "OVERRIDE_DEPRECATION",
               "RemoveRedundantQualifierName")

package com.intellij.openapi.actionSystem.impl

import com.intellij.AbstractBundle
import com.intellij.BundleBase
import com.intellij.DynamicBundle
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.AbbreviationManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionStub
import com.intellij.openapi.actionSystem.ActionStubBase
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Anchor
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.ProjectType
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.findIconUsingNewImplementation
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.util.DefaultBundleService
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.xml.dom.XmlElement
import java.util.ResourceBundle
import java.util.concurrent.CancellationException
import javax.swing.Icon

internal val DYNAMIC_EP_NAME = ExtensionPointName<DynamicActionConfigurationCustomizer>("com.intellij.dynamicActionConfigurationCustomizer")

internal val actionManagerImplLog = logger<ActionManagerImpl>()

internal const val ACTION_ELEMENT_NAME = "action"
internal const val GROUP_ELEMENT_NAME = "group"
internal const val CLASS_ATTR_NAME = "class"
internal const val ID_ATTR_NAME = "id"
internal const val INTERNAL_ATTR_NAME = "internal"
internal const val ICON_ATTR_NAME = "icon"
internal const val ADD_TO_GROUP_ELEMENT_NAME = "add-to-group"
internal const val DESCRIPTION = "description"
internal const val TEXT_ATTR_NAME = "text"
internal const val KEY_ATTR_NAME = "key"
internal const val SEPARATOR_ELEMENT_NAME = "separator"
internal const val REFERENCE_ELEMENT_NAME = "reference"
internal const val GROUP_ID_ATTR_NAME = "group-id"
internal const val REF_ATTR_NAME = "ref"
internal const val USE_SHORTCUT_OF_ATTR_NAME = "use-shortcut-of"
internal const val PROJECT_TYPE = "project-type"
internal const val OVERRIDE_TEXT_ELEMENT_NAME = "override-text"
internal const val SYNONYM_ELEMENT_NAME = "synonym"
internal const val OVERRIDES_ATTR_NAME = "overrides"
internal const val DEACTIVATED_TIMER_DELAY = 5000
internal const val TIMER_DELAY = 500
internal const val UPDATE_DELAY_AFTER_TYPING = 500

internal fun publisher(): AnActionListener {
  return ApplicationManager.getApplication().messageBus.syncPublisher(AnActionListener.TOPIC)
}

internal fun <T> instantiate(
  stubClassName: String,
  pluginDescriptor: PluginDescriptor,
  expectedClass: Class<T>,
  componentManager: ComponentManager,
): T? {
  val obj = try {
    componentManager.instantiateClass<Any>(stubClassName, pluginDescriptor)
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: ProcessCanceledException) {
    throw e
  }
  catch (_: ExtensionNotApplicableException) {
    return null
  }
  catch (e: Throwable) {
    actionManagerImplLog.error(e)
    return null
  }

  if (expectedClass.isInstance(obj)) {
    @Suppress("UNCHECKED_CAST")
    return obj as T
  }
  actionManagerImplLog.error(PluginException("class with name '$stubClassName' must be an instance of '${expectedClass.name}'; " +
                                             "got $obj", pluginDescriptor.pluginId))
  return null
}

internal fun updateIconFromStub(
  stub: ActionStubBase,
  anAction: AnAction,
  componentManager: ComponentManager,
  actionSupplier: (String) -> AnAction?,
) {
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
      customActionsSchema.initActionIcon(anAction = anAction, actionId = stub.id, actionSupplier = actionSupplier)
      null
    }
  }
}

internal fun convertGroupStub(stub: ActionGroupStub, actionRegistrar: ActionRegistrar): ActionGroup? {
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
    else actionRegistrar.state.getActionId(action)
  })
  updateIconFromStub(
    stub = stub,
    anAction = group,
    componentManager = componentManager,
    actionSupplier = { actionRegistrar.getAction(it) },
  )
  return group
}

internal fun processAbbreviationNode(e: XmlElement, id: String) {
  val abbr = e.attributes.get("value")
  if (!abbr.isNullOrEmpty()) {
    val abbreviationManager = AbbreviationManager.getInstance() as AbbreviationManagerImpl
    abbreviationManager.register(abbr, id, true)
  }
}

internal fun isSecondary(element: XmlElement): Boolean = element.attributes.get("secondary").toBoolean()

@Suppress("DEPRECATION")
internal fun loadIcon(module: PluginDescriptor, iconPath: String, requestor: String?): Icon {
  val start = StartUpMeasurer.getCurrentTimeIfEnabled()
  var icon = findIconUsingNewImplementation(path = iconPath, classLoader = module.classLoader)
  if (icon == null) {
    reportActionManagerError(module, "Icon cannot be found in '$iconPath', action '$requestor'")
    icon = AllIcons.Nodes.Unknown
  }
  IconLoadMeasurer.actionIcon.end(start)
  return icon
}

@Suppress("HardCodedStringLiteral")
internal fun computeDescription(
  bundleSupplier: () -> ResourceBundle?,
  id: String,
  elementType: String,
  descriptionValue: String?,
  classLoader: ClassLoader,
): @NlsActions.ActionDescription String? {
  var effectiveBundle = bundleSupplier()
  if (effectiveBundle != null && DefaultBundleService.isDefaultBundle()) {
    effectiveBundle = DynamicBundle.getResourceBundle(classLoader, effectiveBundle.baseBundleName)
  }
  return AbstractBundle.messageOrDefault(effectiveBundle, "$elementType.$id.$DESCRIPTION", descriptionValue ?: "")
}

@Suppress("HardCodedStringLiteral")
internal fun computeActionText(
  bundleSupplier: () -> ResourceBundle?,
  id: String,
  elementType: String,
  textValue: String?,
  classLoader: ClassLoader,
): @NlsActions.ActionText String? {
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

internal fun parseAnchor(anchorStr: String?, actionName: String?, module: IdeaPluginDescriptor): Anchor? {
  return when {
    anchorStr == null -> Anchor.LAST
    "first".equals(anchorStr, ignoreCase = true) -> Anchor.FIRST
    "last".equals(anchorStr, ignoreCase = true) -> Anchor.LAST
    "before".equals(anchorStr, ignoreCase = true) -> Anchor.BEFORE
    "after".equals(anchorStr, ignoreCase = true) -> Anchor.AFTER
    else -> {
      reportActionManagerError(module,
                               "$actionName: anchor should be one of the following constants: \"first\", \"last\", \"before\" or \"after\"")
      null
    }
  }
}

internal fun reportActionManagerError(module: PluginDescriptor, message: String, cause: Throwable? = null) {
  actionManagerImplLog.error(PluginException("$message (module=$module)", cause, module.pluginId))
}

internal fun getPluginInfo(id: PluginId?): String {
  val plugin = (if (id == null) null else PluginManagerCore.getPlugin(id)) ?: return ""
  return " (Plugin: ${plugin.name ?: id!!.idString})"
}

internal fun createActionToolbarImpl(
  place: String,
  group: ActionGroup,
  horizontal: Boolean,
  decorateButtons: Boolean,
  customizable: Boolean,
): ActionToolbarImpl {
  return ActionToolbarImpl(place, group, horizontal, decorateButtons, customizable)
}

internal fun obtainActionId(element: XmlElement, className: String?): String {
  val id = element.attributes.get(ID_ATTR_NAME)
  return if (id.isNullOrEmpty()) StringUtilRt.getShortName(className!!) else id
}

internal fun processOverrideTextNode(
  action: AnAction,
  id: String,
  element: XmlElement,
  module: IdeaPluginDescriptor, bundleSupplier: () -> ResourceBundle?,
) {
  val place = element.attributes.get("place")
  if (place == null) {
    reportActionManagerError(module, "$id: override-text specified without place")
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

@Suppress("HardCodedStringLiteral")
internal fun processSynonymNode(
  action: AnAction,
  element: XmlElement,
  module: IdeaPluginDescriptor,
  bundleSupplier: () -> ResourceBundle?,
) {
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
      reportActionManagerError(module, "Can't process synonym: neither text nor resource bundle key is specified")
    }
  }
}

internal fun createSeparator(bundleSupplier: () -> ResourceBundle?, key: String): Separator {
  val bundle = bundleSupplier()
  val text = if (bundle == null) null else AbstractBundle.messageOrNull(bundle, key)
  return if (text == null) Separator.getInstance() else Separator(text)
}

internal fun getReferenceActionId(element: XmlElement): String? {
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

internal fun updateHandlers(action: Any?) {
  if (action is EditorAction) {
    action.clearDynamicHandlersCache()
  }
}

internal fun convertActionStub(stub: ActionStub, actionSupplier: (String) -> AnAction?): AnAction? {
  val componentManager = ApplicationManager.getApplication() ?: throw AlreadyDisposedException("Application is already disposed")
  val anAction = instantiate(
    stubClassName = stub.className,
    pluginDescriptor = stub.plugin,
    expectedClass = AnAction::class.java,
    componentManager = componentManager,
  ) ?: return null
  stub.initAction(anAction)
  updateIconFromStub(stub = stub, anAction = anAction, componentManager = componentManager, actionSupplier = actionSupplier)
  return anAction
}

internal fun configureGroupDescriptionAndIcon(
  presentation: Presentation,
  @NlsSafe description: String?,
  textValue: String?,
  group: ActionGroup,
  bundleSupplier: () -> ResourceBundle?,
  id: String,
  classLoader: ClassLoader,
  iconPath: String?,
  module: IdeaPluginDescriptorImpl,
  className: String?,
) {
  // don't override value which was set in API with empty value from xml descriptor
  presentation.setFallbackPresentationText {
    computeActionText(bundleSupplier = bundleSupplier,
                      id = id,
                      elementType = GROUP_ELEMENT_NAME,
                      textValue = textValue,
                      classLoader = classLoader)
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
internal fun addToMap(
  actionId: String,
  existing: AnAction?,
  action: AnAction,
  projectType: ProjectType?,
  registrar: ActionRegistrar,
): Boolean {
  val actionSupplier: (String) -> AnAction? = { registrar.getAction(it) }
  when {
    existing is ChameleonAction -> {
      return existing.addAction(action, projectType, actionSupplier)
    }
    existing != null -> {
      // we need to create ChameleonAction even if 'projectType==null', in case 'ActionStub.getProjectType() != null'
      val chameleonAction = ChameleonAction(actionId, existing, null, actionSupplier)
      if (chameleonAction.addAction(action, projectType, actionSupplier)) {
        registrar.putAction(actionId, chameleonAction)
        return true
      }
      return false
    }
    projectType != null -> {
      registrar.putAction(actionId, ChameleonAction(actionId, action, projectType, actionSupplier))
      return true
    }
    else -> {
      registrar.putAction(actionId, action)
      return true
    }
  }
}

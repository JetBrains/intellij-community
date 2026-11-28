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
import com.intellij.openapi.diagnostic.rethrowControlFlowException
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
import com.intellij.platform.icons.AllIconDescriptors
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.swing.toIconDescriptor
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.getClassNameByIconPath
import com.intellij.ui.icons.getReflectiveIcon
import com.intellij.ui.icons.iconDescriptorClassName
import com.intellij.ui.icons.isReflectivePath
import com.intellij.ui.icons.patchIconPath
import com.intellij.util.DefaultBundleService
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.xml.dom.XmlElement
import org.jetbrains.annotations.ApiStatus
import java.util.ResourceBundle
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
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
internal const val KEEP_CONTENT_ATTR_NAME = "keep-content"
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
    setXmlIconSuppliers(anAction.templatePresentation, module = stub.plugin, iconPath = iconPath, requestor = anAction.javaClass.name)
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

/**
 * Sets the lazy icon and the lazy descriptor of the XML `icon` attribute [iconPath]. The two suppliers share one report, so
 * a broken icon reference gives one error, also when both suppliers are read.
 */
@ApiStatus.Internal
fun setXmlIconSuppliers(presentation: Presentation, module: PluginDescriptor, iconPath: String, requestor: String?) {
  val errorReported = AtomicBoolean()
  presentation.setIconSupplier(SynchronizedClearableLazy {
    loadIcon(module = module, iconPath = iconPath, requestor = requestor, errorReported = errorReported)
  })
  presentation.setIconDescriptorSupplier(SynchronizedClearableLazy {
    loadIconDescriptor(module = module, iconPath = iconPath, requestor = requestor, errorReported = errorReported)
  })
}

/** Reports the error, unless [errorReported] tells that the other supplier of the same icon reference reported it. */
private fun reportIconError(module: PluginDescriptor, message: String, errorReported: AtomicBoolean, cause: Throwable? = null) {
  if (errorReported.compareAndSet(false, true)) {
    reportActionManagerError(module, message, cause)
  }
}

@Suppress("DEPRECATION")
internal fun loadIcon(
  module: PluginDescriptor,
  iconPath: String,
  requestor: String?,
  errorReported: AtomicBoolean = AtomicBoolean(),
): Icon {
  val start = StartUpMeasurer.getCurrentTimeIfEnabled()
  var icon = findIconUsingNewImplementation(path = iconPath, classLoader = module.classLoader)
  if (icon == null) {
    reportIconError(module, "Icon cannot be found in '$iconPath', action '$requestor'", errorReported)
    icon = AllIcons.Nodes.Unknown
  }
  IconLoadMeasurer.actionIcon.end(start)
  return icon
}

/**
 * Descriptor counterpart of [loadIcon] for the experimental icons pipeline (see `ExperimentalIconRendering`): the XML `icon`
 * attribute as an [IconDescriptor], see [findIconDescriptor].
 *
 * Like [loadIcon], it reports an icon that cannot be resolved and returns [AllIconDescriptors.Nodes.Unknown] for it. A
 * usage site can thus tell an action without an icon (`null`) from an action whose icon declaration is wrong. It reports
 * once, with the exception as the cause, and not when [errorReported] tells that [loadIcon] reported the same reference.
 */
@ApiStatus.Internal
fun loadIconDescriptor(
  module: PluginDescriptor,
  iconPath: String,
  requestor: String?,
  errorReported: AtomicBoolean = AtomicBoolean(),
): IconDescriptor {
  var cause: Throwable? = null
  val descriptor = try {
    findIconDescriptor(iconPath, module.classLoader)
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    cause = e
    null
  }
  if (descriptor == null) {
    reportIconError(module, "Icon descriptor cannot be found in '$iconPath', action '$requestor'", errorReported, cause)
    return AllIconDescriptors.Nodes.Unknown
  }
  return descriptor
}

/**
 * The [IconDescriptor] of an icon reference as XML declares it, resolved in the order of the legacy `findIconByPath`:
 * 1. a reflective reference (e.g. `AllIcons.Actions.Foo`) is resolved as it is, see [reflectiveIconDescriptor];
 * 2. another path is normalized and patched by the icon path patchers (`icon-robots.txt` redirects old paths, possibly to a
 *    reflective reference or another class loader);
 * 3. a patched path that is a reflective reference is resolved as in step 1;
 * 4. another path becomes an image of the original path. Like the legacy icon, the image loader patches the path when it
 *    loads the image, so that a redirect is followed once and a later theme patches the original path again.
 *
 * Whether an image resource exists is only known when it is rendered. Returns `null` when a reflective reference cannot
 * be resolved, the case in which [loadIcon] falls back too.
 */
@ApiStatus.Internal
fun findIconDescriptor(iconPath: String, classLoader: ClassLoader): IconDescriptor? {
  if (isReflectivePath(iconPath)) {
    return reflectiveIconDescriptor(iconPath, classLoader)
  }
  val originalPath = iconPath.trimStart('/')
  val patched = patchIconPath(originalPath, classLoader)
  val effectivePath = patched?.first ?: originalPath
  val effectiveClassLoader = patched?.second ?: classLoader
  if (isReflectivePath(effectivePath)) {
    return reflectiveIconDescriptor(effectivePath, effectiveClassLoader)
  }
  return imageIconDescriptor(originalPath, classLoader)
}

/**
 * The descriptor of the reflective reference [path]: the generated descriptor of the same name, see
 * [getReflectiveIconDescriptor], or else the legacy icon, see [legacyIconDescriptor].
 */
private fun reflectiveIconDescriptor(path: String, classLoader: ClassLoader): IconDescriptor? {
  return getReflectiveIconDescriptor(path, classLoader) ?: legacyIconDescriptor(path, classLoader)
}

/**
 * The legacy icon behind the reflective reference [path]. A plain `CachedImageIcon` becomes an image of its own resource,
 * when the class loader finds that resource. Another icon is bridged as a Swing icon. Examples are a `Badge`, a
 * `LayeredIcon`, an icon with a filter, and an icon whose path is relative to its class. Returns `null` when [path] cannot
 * be resolved.
 */
private fun legacyIconDescriptor(path: String, classLoader: ClassLoader): IconDescriptor? {
  val icon = getReflectiveIcon(path, classLoader) ?: return null
  val coords = (icon as? CachedImageIcon)?.getPlainImageCoords()
  if (coords != null) {
    val imagePath = coords.first.trimStart('/')
    val imageClassLoader = coords.second
    // the image loader resolves the path from the root of the class loader, and patches it as the legacy loader does
    val patched = patchIconPath(imagePath, imageClassLoader)
    val resourceClassLoader = patched?.second ?: imageClassLoader
    if (resourceClassLoader.getResource((patched?.first ?: imagePath).trimStart('/')) != null) {
      return imageIconDescriptor(imagePath, imageClassLoader)
    }
  }
  return icon.toIconDescriptor()
}

// AllIcons is the one icon holder whose generated descriptors move to a different package (see `IconsClassGenerator`).
private const val ALL_ICONS_CLASS = "com.intellij.icons.AllIcons"
private const val ALL_ICON_DESCRIPTORS_CLASS = "com.intellij.platform.icons.AllIconDescriptors"

/**
 * Gives the generated [IconDescriptor] of a reflective icon reference, for example `AllIcons.Actions.Foo` or
 * `com.example.SomeIcons.Bar`. It reads the descriptor with the generated `@JvmStatic` getter.
 *
 * [toDescriptorClassName] gives the name of the descriptor class. Most icon classes of third-party plugins have no
 * descriptor class. For them, this function returns `null`, and [reflectiveIconDescriptor] uses the legacy icon.
 */
private fun getReflectiveIconDescriptor(path: String, moduleClassLoader: ClassLoader): IconDescriptor? {
  val fieldName = path.substring(path.lastIndexOf('.') + 1)
  // Reuse the legacy icon-path -> holder-class derivation so nested classes and short-name packages match exactly.
  val iconClassName = getClassNameByIconPath(path)
  val descriptorClassName = toDescriptorClassName(iconClassName)

  // AllIconDescriptors is in the `intellij.platform.icons` module. Only the classloader of that module surely loads it
  // and its nested classes. The classloader of a plugin can lack it. The classloader of `IconDescriptor` is the one of
  // `intellij.platform.icons.api`, and it cannot load a class of a module that depends on it.
  // The descriptor class of a third-party plugin is in the module of the plugin, so the module classloader loads it.
  val classLoader = if (descriptorClassName.startsWith(ALL_ICON_DESCRIPTORS_CLASS)) {
    AllIconDescriptors::class.java.classLoader
  }
  else {
    moduleClassLoader
  }

  val holderClass = try {
    classLoader.loadClass(descriptorClassName)
  }
  catch (_: ClassNotFoundException) {
    // no generated descriptor holder for this icon class: the caller falls back to the legacy icon
    return null
  }
  // The generated descriptor holder is a Kotlin object with `@JvmStatic val` fields, i.e. static getters.
  val getter = try {
    holderClass.getMethod("get" + fieldName.replaceFirstChar { it.uppercaseChar() })
  }
  catch (_: NoSuchMethodException) {
    // the holder has no descriptor of this name, for example an icon that is newer than the generated holder: the caller
    // falls back to the legacy icon
    return null
  }
  return getter.invoke(null) as? IconDescriptor
}

/**
 * Converts the name of an icon class, as [getClassNameByIconPath] gives it, into the name of its generated descriptor class.
 * For example, `icons.SomeIcons${'$'}Foo` becomes `icons.SomeIconDescriptors${'$'}Foo`.
 * The simple name follows [iconDescriptorClassName], the rule of the icon class generator.
 * The descriptors of `AllIcons` are in the `com.intellij.platform.icons` package. The other descriptor classes are in the package of the icon class.
 */
@ApiStatus.Internal
fun toDescriptorClassName(iconClassName: String): String {
  // the names of the nested classes do not change
  val nestedStart = iconClassName.indexOf('$')
  val topLevel = if (nestedStart == -1) iconClassName else iconClassName.substring(0, nestedStart)
  val nested = if (nestedStart == -1) "" else iconClassName.substring(nestedStart)
  if (topLevel == ALL_ICONS_CLASS) {
    return ALL_ICON_DESCRIPTORS_CLASS + nested
  }
  val lastDot = topLevel.lastIndexOf('.')
  return topLevel.substring(0, lastDot + 1) + iconDescriptorClassName(topLevel.substring(lastDot + 1)) + nested
}

@Suppress("HardCodedStringLiteral")
internal fun computeDescription(
  bundle: ResourceBundle?,
  id: String,
  elementType: String,
  descriptionValue: String?,
  classLoader: ClassLoader,
): @NlsActions.ActionDescription String? {
  var effectiveBundle = bundle
  if (effectiveBundle != null && DefaultBundleService.isDefaultBundle()) {
    effectiveBundle = DynamicBundle.getResourceBundle(classLoader, effectiveBundle.baseBundleName)
  }
  return AbstractBundle.messageOrDefault(effectiveBundle, "$elementType.$id.$DESCRIPTION", descriptionValue ?: "")
}

@Suppress("HardCodedStringLiteral")
internal fun computeActionText(
  bundle: ResourceBundle?,
  id: String,
  elementType: String,
  textValue: String?,
  classLoader: ClassLoader,
): @NlsActions.ActionText String? {
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
    val text = element.attributes.get(TEXT_ATTR_NAME)
    if (text.isNullOrEmpty()) {
      val prefix = if (action is ActionGroup) "group" else "action"
      val key = "$prefix.$id.$place.text"
      // the resource bundle is loaded when the text is read, not while the actions are registered
      action.addTextOverride(place) { bundleSupplier()?.let { BundleBase.message(it, key) } ?: text }
    }
    else {
      action.addTextOverride(place) { text }
    }
  }
}

/**
 * @param hasBundle whether the element declares a resource bundle, so the misconfiguration is reported without loading it
 */
@Suppress("HardCodedStringLiteral")
internal fun processSynonymNode(
  action: AnAction,
  element: XmlElement,
  module: IdeaPluginDescriptor,
  bundleSupplier: () -> ResourceBundle?,
  hasBundle: Boolean,
) {
  val text = element.attributes.get(TEXT_ATTR_NAME)
  if (!text.isNullOrEmpty()) {
    action.addSynonym { text }
  }
  else {
    val key = element.attributes.get(KEY_ATTR_NAME)
    if (key != null && hasBundle) {
      // the resource bundle is loaded when the synonym is read, not while the actions are registered
      action.addSynonym { bundleSupplier()?.let { BundleBase.message(it, key) } ?: "" }
    }
    else {
      reportActionManagerError(module, "Can't process synonym: neither text nor resource bundle key is specified")
    }
  }
}

// the resource bundle is loaded when the separator text is read, not while the actions are registered
internal fun createSeparator(bundleSupplier: () -> ResourceBundle?, key: String): Separator {
  return Separator { bundleSupplier()?.let { AbstractBundle.messageOrNull(it, key) } }
}

internal fun getReferenceActionId(element: XmlElement): String? {
  // support old style references by id
  return element.attributes.get(REF_ATTR_NAME) ?: element.attributes.get(ID_ATTR_NAME)
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
  presentation.initFallbackText {
    computeActionText(bundle = bundleSupplier(),
                      id = id,
                      elementType = GROUP_ELEMENT_NAME,
                      textValue = textValue,
                      classLoader = classLoader)
  }

  // the description is computed when it is read: computing it loads the resource bundle of the plugin.
  // An empty value from the XML descriptor does not override the value set in API
  presentation.initDescriptionWithFallback {
    val bundle = bundleSupplier()
    if (bundle == null) {
      description
    }
    else {
      computeDescription(bundle = bundle,
                         id = id,
                         elementType = GROUP_ELEMENT_NAME,
                         descriptionValue = description,
                         classLoader = classLoader)
    }
  }

  if (iconPath != null && group !is ActionGroupStub) {
    setXmlIconSuppliers(presentation, module = module, iconPath = iconPath, requestor = className)
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

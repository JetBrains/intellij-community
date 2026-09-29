// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.properties.BundleNameEvaluator
import com.intellij.lang.properties.PropertiesImplUtil
import com.intellij.lang.properties.PropertiesReferenceManager
import com.intellij.lang.properties.psi.PropertyKeyIndex
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.ElementManipulators
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.XmlElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.index.PluginIdDependenciesIndex
import org.jetbrains.idea.devkit.inspections.DeclareResourceBundleFix
import org.jetbrains.idea.devkit.references.PluginConfigReference
import org.jetbrains.idea.devkit.util.DescriptorUtil
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

private const val STRIPE_TITLE_KEY_PREFIX = "toolwindow.stripe."
private const val IDE_BUNDLE_NAME = "messages.IdeBundle"

private const val TOOL_WINDOW_EP_CLASS = "com.intellij.openapi.wm.ToolWindowEP"

private const val TOOL_WINDOW_CLASS = "com.intellij.openapi.wm.ToolWindow"

/**
 * A factory that calls one of these from `init` sets the stripe title itself, so the resource bundle does not matter.
 *
 * The short title is a separate string. A factory that sets only the short title still needs the key,
 * so `setStripeShortTitleProvider` does not belong here.
 *
 * @see com.intellij.openapi.wm.ToolWindowFactory.init
 */
private val STRIPE_TITLE_SETTERS = setOf("setStripeTitle", "setStripeTitleProvider")

/** Kotlin assigns `toolWindow.stripeTitle = ...` through a synthetic property, which can resolve to its getter. */
private val STRIPE_TITLE_PROPERTY_ACCESSORS = STRIPE_TITLE_SETTERS + setOf("getStripeTitle", "getStripeTitleProvider")

/**
 * Reports wrong `toolwindow.stripe.<ID>` keys
 *
 * The platform reads the key from one bundle only. See `com.intellij.toolWindow.getStripeTitleSupplier`.
 * The inspection stays silent when it cannot prove which bundle the platform reads.
 */
internal class ToolWindowStripeTitleInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val file = holder.file
    if (!DevKitInspectionUtil.isAllowed(file)) return PsiElementVisitor.EMPTY_VISITOR

    return when {
      file is XmlFile && DescriptorUtil.isPluginXml(file) -> DescriptorVisitor(holder)
      PropertiesImplUtil.getPropertiesFile(file) != null -> BundleVisitor(holder)
      else -> PsiElementVisitor.EMPTY_VISITOR
    }
  }
}

/**
 * Highlights the `id` attribute of a `<toolWindow>` extension.
 */
private class DescriptorVisitor(private val holder: ProblemsHolder) : XmlElementVisitor() {

  override fun visitXmlTag(tag: XmlTag) {
    if (!isToolWindowExtension(tag)) return

    val idAttribute = tag.getAttribute("id") ?: return
    val target = idAttribute.valueElement ?: return
    val toolWindowId = idAttribute.value?.trim()?.takeIf { it.isNotEmpty() } ?: return

    val source = stripeTitleSource(tag) ?: return
    val readBundle = source.readBundle
    val key = stripeTitleKey(toolWindowId)

    val expectedName = readBundle.name
    if (expectedName != null && bundleHasKey(source.module, expectedName, key)) return

    val actualBundle = bundlesWithKey(source.module, key).firstOrNull { it != expectedName }
      ?: return // No bundle holds the key, so the ID is the title. That is normal for a tool window that isn't localized.

    val idRange = ElementManipulators.getValueTextRange(target)
    val moduleName = source.module.name

    when (readBundle) {
      is ReadBundle.Declared -> holder.registerProblem(
        target, idRange,
        DevKitBundle.message("inspection.tool.window.stripe.title.wrong.bundle", moduleName, readBundle.name, key, actualBundle)
      )
      is ReadBundle.CoreIdeBundle -> holder.registerProblem(
        target, idRange,
        DevKitBundle.message("inspection.tool.window.stripe.title.core.bundle", moduleName, key, actualBundle)
      )
      is ReadBundle.NotDeclared -> holder.registerProblem(
        target, idRange,
        DevKitBundle.message("inspection.tool.window.stripe.title.no.bundle", moduleName, key, actualBundle),
        DeclareResourceBundleFix(actualBundle, source.descriptor)
      )
    }
  }
}

private class BundleVisitor(private val holder: ProblemsHolder) : PsiElementVisitor() {

  override fun visitElement(element: PsiElement) {
    if (element !is PropertyKeyImpl) return

    val key = element.text
    if (!key.startsWith(STRIPE_TITLE_KEY_PREFIX)) return

    val file = element.containingFile ?: return
    val thisBundleName = bundleNameOf(file) ?: return
    // A language pack holds a copy of a key. The platform reads it through the bundle it translates.
    if (isLocalizedCopy(thisBundleName)) return

    val extension = resolveToolWindowExtension(element) ?: return
    val source = stripeTitleSource(extension) ?: return
    val readBundle = source.readBundle

    val expectedName = readBundle.name
    if (expectedName == thisBundleName) return
    // The key is also in the bundle the platform reads, so the stripe title works. This copy is only dead weight.
    if (expectedName != null && bundleHasKey(source.module, expectedName, key)) return

    // The key turns every space into an underscore, so only the extension knows the real ID.
    val toolWindowId = extension.getAttributeValue("id")?.trim() ?: return
    val moduleName = source.module.name
    when (readBundle) {
      is ReadBundle.Declared, is ReadBundle.CoreIdeBundle -> holder.registerProblem(
        element,
        DevKitBundle.message("inspection.tool.window.stripe.title.property.wrong.bundle", moduleName, toolWindowId, readBundle.name)
      )
      is ReadBundle.NotDeclared -> holder.registerProblem(
        element,
        DevKitBundle.message("inspection.tool.window.stripe.title.property.no.bundle", moduleName, toolWindowId),
        DeclareResourceBundleFix(thisBundleName, source.descriptor)
      )
    }
  }
}

/**
 * Declares the bundle that holds the key. Only a descriptor without a declaration gets this fix:
 * a change of an existing declaration also moves every other key that the descriptor reads through it.
 *
 * On the key side, the problem is in the bundle, but the fix changes the descriptor.
 */
private class DeclareResourceBundleFix(private val bundleName: String, descriptor: XmlFile) : PsiUpdateModCommandQuickFix() {

  private val descriptor: SmartPsiElementPointer<XmlFile> = SmartPointerManager.createPointer(descriptor)

  override fun getName(): @IntentionName String =
    DevKitBundle.message("inspection.tool.window.stripe.title.declare.bundle.fix", bundleName)

  override fun getFamilyName(): @IntentionFamilyName String =
    DevKitBundle.message("inspection.tool.window.stripe.title.declare.bundle.fix.family")

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val writableDescriptor = updater.getWritable(descriptor.element ?: return)
    val ideaPlugin = DescriptorUtil.getIdeaPlugin(writableDescriptor) ?: return
    ideaPlugin.resourceBundle.stringValue = bundleName
  }
}

/**
 * The bundle that `getStripeTitleSupplier` reads for a descriptor.
 */
private sealed class ReadBundle(val name: String?) {
  /** The descriptor declares the bundle, and the platform reads it. */
  class Declared(name: String) : ReadBundle(name)

  /** The core plugin owns the module, so the platform ignores the declaration and reads `IdeBundle`. */
  object CoreIdeBundle : ReadBundle(IDE_BUNDLE_NAME)

  /** The descriptor declares no bundle, so the platform reads nothing. */
  object NotDeclared : ReadBundle(null)
}

/**
 * Answers `null` when the owner of the descriptor is unclear. The inspection then stays silent.
 */
private fun readBundle(descriptor: XmlFile): ReadBundle? {
  val plugin = DescriptorUtil.getIdeaPlugin(descriptor) ?: return null
  val declared = plugin.resourceBundle.stringValue?.trim()?.takeIf { it.isNotEmpty() }

  // A descriptor with its own <id> is a main plugin descriptor.
  plugin.pluginId?.let { return readBundle(it, declared) }

  // A descriptor without an <id> is a content module. It does not inherit the bundle of its plugin.
  val virtualFile = descriptor.virtualFile ?: return null
  val project = descriptor.project
  val owners = PluginIdDependenciesIndex.findFilesIncludingContentModule(project, virtualFile)
  val ownerIds = owners.mapNotNull { PluginIdDependenciesIndex.getPluginId(project, it) }.toSet()
  val ownerId = ownerIds.singleOrNull() ?: return null
  return readBundle(ownerId, declared)
}

private fun readBundle(pluginId: String, declaredBundleName: String?): ReadBundle = when {
  pluginId == PluginManagerCore.CORE_PLUGIN_ID -> ReadBundle.CoreIdeBundle
  declaredBundleName != null -> ReadBundle.Declared(declaredBundleName)
  else -> ReadBundle.NotDeclared
}

private fun stripeTitleKey(toolWindowId: String): String = STRIPE_TITLE_KEY_PREFIX + toolWindowId.replace(' ', '_')

/**
 * The platform reads the stripe title for every extension with a [TOOL_WINDOW_EP_CLASS] bean,
 * such as `toolWindow`, `library.toolWindow` and `facet.toolWindow`.
 *
 * @see com.intellij.openapi.wm.impl.ToolWindowManagerImpl.initToolWindow
 */
private fun isToolWindowExtension(tag: XmlTag): Boolean {
  if (tag.parentTag?.localName != "extensions") return false
  val extension = DomManager.getDomManager(tag.project).getDomElement(tag) as? Extension ?: return false
  return InheritanceUtil.isInheritor(extension.extensionPoint?.beanClass?.value, TOOL_WINDOW_EP_CLASS)
}

private fun bundleNameOf(file: PsiFile): String? = BundleNameEvaluator.DEFAULT.evaluateBundleName(file)

/**
 * A translated bundle lives under a `localization` directory in this repository.
 */
private fun isLocalizedCopy(bundleName: String): Boolean = bundleName.splitToSequence('.').any { it == "localization" }

private fun bundleHasKey(module: Module, bundleName: String, key: String): Boolean {
  return PropertiesReferenceManager.getInstance(module.project)
    .findPropertiesFiles(module, bundleName)
    .any { it.findPropertyByKey(key) != null }
}

private fun bundlesWithKey(module: Module, key: String): List<String> {
  val project = module.project
  val scope = GlobalSearchScope.moduleRuntimeScope(module, false)
  return PropertyKeyIndex.getInstance().getProperties(key, project, scope)
    .mapNotNull { property -> property.containingFile?.let { bundleNameOf(it) } }
    .filterNot { isLocalizedCopy(it) }
    .distinct()
}

/**
 * Follows the reference that [org.jetbrains.idea.devkit.references.MessageBundleReferenceContributor] puts on the key.
 *
 * The reference resolves to the `id` attribute of the extension, so the result is its enclosing tag.
 * The reference resolves only to a `com.intellij.toolWindow` extension. A key of any other tool window extension stays silent.
 */
private fun resolveToolWindowExtension(propertyKey: PsiElement): XmlTag? {
  for (reference in propertyKey.references) {
    if (reference !is PluginConfigReference) continue
    val resolved = reference.resolve() ?: continue
    val element = resolved.navigationElement ?: resolved
    val tag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java, false) ?: continue
    if (isToolWindowExtension(tag)) return tag
  }
  return null
}

/**
 * What the platform reads for one `<toolWindow>` extension.
 */
private class StripeTitleSource(val descriptor: XmlFile, val module: Module, val readBundle: ReadBundle)

/**
 * Answers `null` when the inspection must stay silent. Both report sides share this decision, so they cannot drift.
 */
private fun stripeTitleSource(extension: XmlTag): StripeTitleSource? {
  val descriptor = extension.containingFile as? XmlFile ?: return null
  val module = ModuleUtilCore.findModuleForPsiElement(extension) ?: return null

  val readBundle = readBundle(descriptor) ?: return null
  // This resolves a class and walks its body, so it runs after the cheap index lookups above.
  if (setsStripeTitleInCode(extension, module)) return null

  return StripeTitleSource(descriptor, module, readBundle)
}

/**
 * Only a call from `init` counts. The platform calls `createToolWindowContent` when the tool window first opens,
 * so a title set there leaves the bundle title on the stripe until then.
 * A call under a condition counts too. The inspection cannot tell when the condition holds, so it stays silent.
 */
private fun setsStripeTitleInCode(tag: XmlTag, module: Module): Boolean {
  val factoryClassName = tag.getAttributeValue("factoryClass") ?: return false
  val scope = GlobalSearchScope.moduleRuntimeScope(module, false)
  val factoryClass = JavaPsiFacade.getInstance(tag.project).findClass(factoryClassName, scope) ?: return false
  return factoryClass.findMethodsByName("init", true).any { callsStripeTitleSetter(it) }
}

private fun callsStripeTitleSetter(method: PsiMethod): Boolean {
  if (method is PsiCompiledElement) return false

  val uMethod = method.toUElementOfType<UMethod>() ?: return false
  var found = false
  uMethod.accept(object : AbstractUastVisitor() {
    override fun visitCallExpression(node: UCallExpression): Boolean {
      if (!found && isToolWindowMethod(node.resolve(), STRIPE_TITLE_SETTERS)) found = true
      return found
    }

    override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
      if (!found && node.operator == UastBinaryOperator.ASSIGN) {
        val resolved = (node.leftOperand as? UResolvable)?.resolve()
        if (isToolWindowMethod(resolved, STRIPE_TITLE_PROPERTY_ACCESSORS)) found = true
      }
      return found
    }
  })
  return found
}

/**
 * The resolve keeps out a read of the title and an unrelated method with the same name.
 */
private fun isToolWindowMethod(element: PsiElement?, names: Set<String>): Boolean {
  val method = element as? PsiMethod ?: return false
  return method.name in names && InheritanceUtil.isInheritor(method.containingClass, TOOL_WINDOW_CLASS)
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.structureView

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.ui.tree.LeafState
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.core.script.k2.configurations.ScriptConfigurationsProviderImpl
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.idea.core.script.k2.getVirtualFile
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.shared.definition.kotlinScriptTemplate
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependencyAware
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.importScripts
import kotlin.script.experimental.api.providedProperties
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.configurationDependencies
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm
import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.Icon

internal fun createScriptFileRoot(file: KtFile, declarationsRoot: TreeElement): StructureViewTreeElement =
    KotlinScriptFileStructureViewElement(
        file = file,
        contextChildren = createScriptContextChildren(file).toList(),
        declarationsRoot = declarationsRoot,
    )

internal fun createScriptContextChildren(parent: KtFile): Array<StructureViewTreeElement> {
    if (!parent.isScript()) return StructureViewTreeElement.EMPTY_ARRAY
    val context = KotlinScriptStructureViewContext(parent)
    return arrayOf(
        ScriptDefinitionGroupStructureViewElement(context),
        ScriptConfigurationGroupStructureViewElement(context),
    )
}

private class KotlinScriptFileStructureViewElement(
    private val file: KtFile,
    private val contextChildren: List<StructureViewTreeElement>,
    private val declarationsRoot: TreeElement,
) : StructureViewTreeElement, ItemPresentation {
    override fun getValue(): Any = file

    override fun getChildren(): Array<TreeElement> = buildList<TreeElement> {
        addAll(contextChildren)
        add(ScriptDeclarationsGroupStructureViewElement(declarationsRoot))
    }.toTypedArray()

    override fun getPresentation(): ItemPresentation = this

    override fun getPresentableText(): String = file.name

    override fun getLocationString(): String? = null

    override fun getIcon(unused: Boolean): Icon? = file.getIcon(0)

    override fun navigate(requestFocus: Boolean) {
        file.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = file.canNavigate()

    override fun canNavigateToSource(): Boolean = file.canNavigateToSource()
}

private class KotlinScriptStructureViewContext(val file: KtFile) {
    private val project = file.project

    private val snapshot: ScriptStructureSnapshot
        get() = getCachedScriptStructureSnapshot(file)

    val definitionId: String
        get() = snapshot.definitionId

    val definitionType: KotlinType?
        get() = snapshot.definitionType

    val implicitReceiverTypes: List<KotlinType>
        get() = snapshot.implicitReceiverTypes

    val providedProperties: List<ScriptProvidedProperty>
        get() = snapshot.providedProperties

    val defaultImports: List<String>
        get() = snapshot.defaultImports

    val importedScripts: List<ScriptPathEntry>
        get() = snapshot.importedScripts

    val jdkHome: File?
        get() = snapshot.jdkHome

    fun definitionClasspath(): List<ScriptPathEntry> = snapshot.definitionClasspath

    fun scriptClasspath(): List<ScriptPathEntry> = snapshot.scriptClasspath

    private fun definitionClasspathPaths(): List<Path> = scriptDefinition
        ?.hostConfiguration
        ?.get(ScriptingHostConfiguration.configurationDependencies)
        .orEmpty()
        .asSequence()
        .filterIsInstance<JvmDependency>()
        .flatMap { it.classpathPaths() }
        .distinct()
        .sortedBy { it.toString() }
        .toList()

    fun findTypeNavigationTarget(type: KotlinType): Navigatable? {
        if (DumbService.isDumb(project)) return null

        val qualifiedName = type.fromClass?.qualifiedName ?: type.typeName
        val scope = ScriptDependencyAware.getInstance(project)
            .getScriptDependenciesClassFilesScope(file.virtualFile)
            .uniteWith(GlobalSearchScope.allScope(project))

        JavaPsiFacade.getInstance(project).findClass(qualifiedName, scope)?.let { psiClass ->
            if (psiClass.canNavigate()) return psiClass

            val navigationElement = psiClass.navigationElement
            val navigatableElement = navigationElement as? Navigatable
            if (navigatableElement?.canNavigate() == true) return navigatableElement

            return null
        }
        return null
    }

    private val cachedCompilationConfiguration: ScriptCompilationConfiguration? by lazy(LazyThreadSafetyMode.NONE) {
        ScriptConfigurationsProviderImpl.getInstance(project)
            .getScriptCompilationConfiguration(KtFileScriptSource(file), providedConfiguration = null)
            ?.valueOrNull()
            ?.configuration
    }

    private val scriptDefinition by lazy(LazyThreadSafetyMode.NONE) {
        file.findScriptDefinition()
    }

    private fun resolveDefinitionId(): String = scriptDefinition?.definitionId
        ?.takeIf(String::isNotBlank)
        ?: cachedCompilationConfiguration
            ?.get(ScriptCompilationConfiguration.ide.kotlinScriptTemplate)
            ?.id
            ?.takeIf(String::isNotBlank)
        ?: KotlinBaseScriptingBundle.message("structure.view.script.definition.unknown")

    private fun toScriptPathEntry(path: Path): ScriptPathEntry {
        val localFile = LocalFileSystem.getInstance().findFileByNioFile(path)
        val virtualFile = localFile?.let { JarFileSystem.getInstance().getVirtualFileForJar(it) ?: it }
        return ScriptPathEntry(
            name = path.fileName?.toString() ?: path.toString(),
            path = path.toString(),
            location = localFile?.parent?.toPresentableLocation(file.project) ?: path.parent?.toString(),
            virtualFile = virtualFile,
        )
    }

    fun computeSnapshot(): ScriptStructureSnapshot {
        val resolvedConfiguration = cachedCompilationConfiguration
        val definitionId = resolveDefinitionId()
        val definitionClasspathPaths = definitionClasspathPaths()
        val definitionClasspath = definitionClasspathPaths
            .map(::toScriptPathEntry)
            .toList()
        val importedScripts = resolvedConfiguration
            ?.get(ScriptCompilationConfiguration.importScripts)
            .orEmpty()
        val importedScriptEntries = importedScripts
            .mapNotNull(::toImportedScriptEntry)
            .distinctBy(ScriptPathEntry::path)
            .sortedWith(compareBy<ScriptPathEntry> { it.name.lowercase() }.thenBy { it.path })
            .toList()
        val scriptClasspath = resolvedConfiguration
            ?.get(ScriptCompilationConfiguration.dependencies)
            .orEmpty()
            .asSequence()
            .filterIsInstance<JvmDependency>()
            .flatMap { it.classpathPaths() }
            .map(::toScriptPathEntry)
            .distinctBy(ScriptPathEntry::path)
            .sortedWith(compareBy<ScriptPathEntry> { it.name.lowercase() }.thenBy { it.path })
            .toList()

        return ScriptStructureSnapshot(
            definitionId = definitionId,
            definitionType = scriptDefinition?.baseClassType,
            implicitReceiverTypes = resolvedConfiguration?.get(ScriptCompilationConfiguration.implicitReceivers).orEmpty(),
            providedProperties = resolvedConfiguration?.get(ScriptCompilationConfiguration.providedProperties)
                .orEmpty()
                .map { (name, type) -> ScriptProvidedProperty(name, type) },
            defaultImports = resolvedConfiguration?.get(ScriptCompilationConfiguration.defaultImports).orEmpty(),
            importedScripts = importedScriptEntries,
            jdkHome = resolvedConfiguration?.get(ScriptCompilationConfiguration.jvm.jdkHome),
            definitionClasspath = definitionClasspath,
            scriptClasspath = scriptClasspath,
        )
    }

    private fun toImportedScriptEntry(source: SourceCode): ScriptPathEntry? {
        val virtualFile = getVirtualFile(source)
        if (virtualFile != null) {
            return ScriptPathEntry(
                name = virtualFile.name,
                path = virtualFile.path,
                location = virtualFile.parent?.path,
                virtualFile = virtualFile,
            )
        }

        val locationId = source.locationId ?: return null
        val path = try {
            Path.of(locationId)
        } catch (_: InvalidPathException) {
            null
        }

        return ScriptPathEntry(
            name = path?.fileName?.toString() ?: locationId.substringAfterLast('/').substringAfterLast('\\'),
            path = locationId,
            location = path?.parent?.toString(),
            virtualFile = null,
        )
    }
}

private data class ScriptPathEntry(
    val name: String,
    val path: String,
    val location: String?,
    val virtualFile: VirtualFile?,
)

private data class ScriptStructureSnapshot(
    val definitionId: String,
    val definitionType: KotlinType?,
    val implicitReceiverTypes: List<KotlinType>,
    val providedProperties: List<ScriptProvidedProperty>,
    val defaultImports: List<String>,
    val importedScripts: List<ScriptPathEntry>,
    val jdkHome: File?,
    val definitionClasspath: List<ScriptPathEntry>,
    val scriptClasspath: List<ScriptPathEntry>,
)

private data class ScriptProvidedProperty(
    val name: @NlsSafe String,
    val type: KotlinType,
)

private fun KotlinScriptStructureViewContext.hasDefinitionClasspath(): Boolean = definitionClasspath().isNotEmpty()

private data class ScriptStructureNodeValue(
    val filePath: String,
    val kind: String,
    val key: String,
)

private fun getCachedScriptStructureSnapshot(file: KtFile): ScriptStructureSnapshot {
    val project = file.project
    return CachedValuesManager.getManager(project).getCachedValue(file) {
        CachedValueProvider.Result.create(
            KotlinScriptStructureViewContext(file).computeSnapshot(),
            file,
            ScriptDefinitionsModificationTracker.getInstance(project),
            ScriptDependenciesModificationTracker.getInstance(project),
        )
    }
}

private class ScriptDefinitionGroupStructureViewElement(
    context: KotlinScriptStructureViewContext,
) : KotlinScriptStructureViewElement(
    context = context,
    sortOrder = 100,
    icon = null,
    text = { KotlinBaseScriptingBundle.message("structure.view.script.definition.group") },
) {
    override fun children(): List<TreeElement> = buildList {
        add(
            NavigatableScriptStructureViewElement(
                context = context,
                sortOrder = 0,
                icon = AllIcons.Nodes.Class,
                text = { context.definitionId },
                navigate = { context.definitionType?.let(context::findTypeNavigationTarget) },
            )
        )
        if (context.hasDefinitionClasspath()) {
            add(ScriptClassPathGroupStructureViewElement(context, kind = ScriptClassPathKind.Definition))
        }
    }
}

private class ScriptConfigurationGroupStructureViewElement(
    context: KotlinScriptStructureViewContext,
) : KotlinScriptStructureViewElement(
    context = context,
    sortOrder = 200,
    icon = null,
    text = { KotlinBaseScriptingBundle.message("structure.view.script.configuration.group") },
) {
    override fun children(): List<TreeElement> = buildList {
        add(ScriptJdkGroupStructureViewElement(context))
        if (context.implicitReceiverTypes.isNotEmpty()) {
            add(ScriptImplicitReceiversGroupStructureViewElement(context))
        }
        if (context.providedProperties.isNotEmpty()) {
            add(ScriptProvidedPropertiesGroupStructureViewElement(context))
        }
        if (context.defaultImports.isNotEmpty()) {
            add(ScriptImportsGroupStructureViewElement(context))
        }
        if (context.importedScripts.isNotEmpty()) {
            add(ScriptImportedScriptsGroupStructureViewElement(context))
        }
        if (context.scriptClasspath().isNotEmpty()) {
            add(ScriptClassPathGroupStructureViewElement(context, kind = ScriptClassPathKind.Script))
        }
    }
}

private class ScriptJdkGroupStructureViewElement(
    context: KotlinScriptStructureViewContext,
) : KotlinScriptStructureViewElement(
    context = context,
    sortOrder = 250,
    icon = null,
    text = { KotlinBaseScriptingBundle.message("structure.view.script.jdk") },
    ) {
    override fun children(): List<TreeElement> = listOf(
        SimpleScriptStructureViewElement(
            context = context,
            sortOrder = 0,
            icon = AllIcons.Nodes.PpLib,
            text = { context.jdkHome?.toPresentableJdkPath() ?: KotlinBaseScriptingBundle.message("structure.view.script.jdk.none") },
        )
    )
}

private class ScriptImplicitReceiversGroupStructureViewElement(
    context: KotlinScriptStructureViewContext,
) : KotlinScriptStructureViewElement(
    context = context,
    sortOrder = 300,
    icon = null,
    text = { KotlinBaseScriptingBundle.message("structure.view.script.implicit.receivers") },
) {
    override fun children(): List<TreeElement> = context.implicitReceiverTypes.mapIndexed { index, type ->
        NavigatableScriptStructureViewElement(
            context = context,
            sortOrder = index,
            icon = AllIcons.Nodes.Class,
            text = { renderType(type) },
            navigate = { context.findTypeNavigationTarget(type) },
        )
    }
}

private class ScriptProvidedPropertiesGroupStructureViewElement(
    context: KotlinScriptStructureViewContext,
) : KotlinScriptStructureViewElement(
    context = context,
    sortOrder = 500,
    icon = null,
    text = { KotlinBaseScriptingBundle.message("structure.view.script.provided.properties") },
) {
    override fun children(): List<TreeElement> = context.providedProperties.mapIndexed { index, property ->
        ScriptProvidedPropertyStructureViewElement(
            context = context,
            property = property,
            sortOrder = index,
        )
    }
}

private class ScriptImportsGroupStructureViewElement(
    context: KotlinScriptStructureViewContext,
) : KotlinScriptStructureViewElement(
    context = context,
    sortOrder = 1000,
    icon = null,
    text = { KotlinBaseScriptingBundle.message("structure.view.script.default.imports") },
) {
    override fun children(): List<TreeElement> = context.defaultImports.mapIndexed { index, importPath ->
        SimpleScriptStructureViewElement(
            context = context,
            sortOrder = index,
            icon = AllIcons.Nodes.Class,
            text = { importPath },
        )
    }
}

private class ScriptImportedScriptsGroupStructureViewElement(
    context: KotlinScriptStructureViewContext,
) : KotlinScriptStructureViewElement(
    context = context,
    sortOrder = 1050,
    icon = null,
    text = { KotlinBaseScriptingBundle.message("structure.view.script.imported.scripts") },
) {
    override fun children(): List<TreeElement> = context.importedScripts.mapIndexed { index, path ->
        ScriptPathStructureViewElement(
            context = context,
            path = path,
            sortOrder = index,
            icon = KotlinIcons.SCRIPT,
        )
    }
}

private class ScriptDeclarationsGroupStructureViewElement(
    private val declarationsRoot: TreeElement,
) : StructureViewTreeElement, SortableTreeElement, ItemPresentation {
    private val text = KotlinBaseScriptingBundle.message("structure.view.script.declarations.group")
    private val sortOrder: Int = 300

    override fun getValue(): Any = ScriptStructureNodeValue(
        filePath = "",
        kind = javaClass.name,
        key = text,
    )

    override fun getPresentation(): ItemPresentation = this

    override fun getChildren(): Array<TreeElement> = declarationsRoot.children

    override fun getAlphaSortKey(): String = sortKey(sortOrder, text)

    override fun getPresentableText(): String = text

    override fun getLocationString(): String? = null

    override fun getIcon(unused: Boolean): Icon? = null

    override fun navigate(requestFocus: Boolean) = Unit

    override fun canNavigate(): Boolean = false

    override fun canNavigateToSource(): Boolean = false
}

private abstract class KotlinScriptStructureViewElement(
    protected val context: KotlinScriptStructureViewContext,
    private val sortOrder: Int,
    private val icon: Icon?,
    private val text: () -> String,
    private val location: () -> String? = { null },
) : StructureViewTreeElement, SortableTreeElement, ItemPresentation {

    override fun getValue(): Any = value()

    override fun getPresentation(): ItemPresentation = this

    override fun getChildren(): Array<TreeElement> = children().toTypedArray()

    override fun getAlphaSortKey(): String = sortKey(sortOrder, text())

    override fun getPresentableText(): String = text()

    override fun getLocationString(): String? = location()

    override fun getIcon(unused: Boolean): Icon? = icon

    override fun navigate(requestFocus: Boolean) {
        navigatable()?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = navigatable()?.canNavigate() == true

    override fun canNavigateToSource(): Boolean = navigatable()?.canNavigateToSource() == true

    protected open fun children(): List<TreeElement> = emptyList()

    protected open fun value(): Any = ScriptStructureNodeValue(
        filePath = context.file.virtualFile.path,
        kind = javaClass.name,
        key = text(),
    )

    protected open fun navigatable(): Navigatable? = value() as? Navigatable
}

private abstract class KotlinScriptLeafStructureViewElement(
    context: KotlinScriptStructureViewContext,
    sortOrder: Int,
    icon: Icon?,
    text: () -> String,
    location: () -> String? = { null },
) : KotlinScriptStructureViewElement(
    context = context,
    sortOrder = sortOrder,
    icon = icon,
    text = text,
    location = location,
), LeafState.Supplier {
    override fun getLeafState(): LeafState = LeafState.ALWAYS
}

private class SimpleScriptStructureViewElement(
    context: KotlinScriptStructureViewContext,
    sortOrder: Int,
    icon: Icon?,
    text: () -> String,
) : KotlinScriptLeafStructureViewElement(
    context = context,
    sortOrder = sortOrder,
    icon = icon,
    text = text,
)

private open class NavigatableScriptStructureViewElement(
    context: KotlinScriptStructureViewContext,
    sortOrder: Int,
    icon: Icon?,
    text: () -> String,
    private val navigate: () -> Navigatable?,
) : KotlinScriptLeafStructureViewElement(
    context = context,
    sortOrder = sortOrder,
    icon = icon,
    text = text,
) {
    override fun navigatable(): Navigatable? = navigate()
}

private class ScriptProvidedPropertyStructureViewElement(
    context: KotlinScriptStructureViewContext,
    private val property: ScriptProvidedProperty,
    sortOrder: Int,
) : NavigatableScriptStructureViewElement(
    context = context,
    sortOrder = sortOrder,
    icon = AllIcons.Nodes.Variable,
    text = { property.name + ": " + renderType(property.type) },
    navigate = { context.findTypeNavigationTarget(property.type) },
)

private enum class ScriptClassPathKind(val sortOrder: Int, val presentableNameKey: String) {
    Definition(100, "structure.view.script.definition.classpath.definition"),
    Script(1100, "structure.view.script.definition.classpath.script"),
}

private class ScriptClassPathGroupStructureViewElement(
    context: KotlinScriptStructureViewContext,
    private val kind: ScriptClassPathKind,
) : KotlinScriptStructureViewElement(
    context = context,
    sortOrder = kind.sortOrder,
    icon = null,
    text = { KotlinBaseScriptingBundle.message(kind.presentableNameKey) },
) {
    override fun children(): List<TreeElement> {
        val paths = when (kind) {
            ScriptClassPathKind.Definition -> context.definitionClasspath()
            ScriptClassPathKind.Script -> context.scriptClasspath()
        }

        return paths.mapIndexed { index, path ->
            ScriptPathStructureViewElement(context, path, index)
        }
    }
}

private class ScriptPathStructureViewElement(
    context: KotlinScriptStructureViewContext,
    private val path: ScriptPathEntry,
    sortOrder: Int,
    icon: Icon? = AllIcons.Nodes.PpLib,
) : KotlinScriptLeafStructureViewElement(
    context = context,
    sortOrder = sortOrder,
    icon = icon,
    text = { path.name },
    location = { path.location },
) {
    override fun value(): Any = path.virtualFile ?: path.path

    override fun navigatable(): Navigatable? = path.virtualFile?.let { OpenFileDescriptor(context.file.project, it) }
}

private fun VirtualFile.toPresentableLocation(project: com.intellij.openapi.project.Project): String {
    val presentableUrl = presentableUrl
    val projectBasePath = project.basePath
    return if (projectBasePath != null && FileUtil.startsWith(presentableUrl, projectBasePath)) {
        "..." + presentableUrl.substring(projectBasePath.length)
    } else {
        FileUtil.getLocationRelativeToUserHome(presentableUrl, false)
    }
}

private fun File.toPresentableJdkPath(): String {
    var home = path
    home = StringUtil.trimEnd(home, "/Contents/Home")
    home = StringUtil.trimEnd(home, "/Contents/MacOS")
    home = FileUtil.getLocationRelativeToUserHome(home, false)
    return StringUtil.shortenTextWithEllipsis(home, 50, 30)
}

private fun sortKey(order: Int, text: String): String = order.toString().padStart(4, '0') + ':' + text

private fun renderType(type: KotlinType): @NlsSafe String {
    val typeName = type.typeName
    return if (type.isNullable) "$typeName?" else typeName
}

@Suppress("UseOfFileInsteadOfPath")
//noinspection UseOfFileInsteadOfPath
private fun JvmDependency.classpathPaths(): Sequence<Path> = classpath.asSequence().map { it.toPath() }

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script

import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.ide.script.IdeConsoleRootType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ex.PathUtilEx
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.containers.SLRUMap
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.caches.project.SdkInfo
import org.jetbrains.kotlin.idea.caches.project.getScriptRelatedModuleInfo
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.core.util.withCheckCanceledLock
import org.jetbrains.kotlin.idea.core.util.writeWithCheckCanceled
import org.jetbrains.kotlin.idea.util.application.getServiceSafe
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.getProjectJdkTableSafe
import org.jetbrains.kotlin.script.ScriptTemplatesProvider
import org.jetbrains.kotlin.scripting.definitions.*
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.flattenTo
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.experimental.dependencies.ScriptDependencies
import kotlin.script.experimental.dependencies.asSuccess
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.configurationDependencies
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.util.ClasspathExtractionException
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContextOrStdlib
import kotlin.script.templates.standard.ScriptTemplateWithArgs

class LoadScriptDefinitionsStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        if (isUnitTestMode()) {
            // In tests definitions are loaded synchronously because they are needed to analyze script
            // In IDE script won't be highlighted before all definitions are loaded, then the highlighting will be restarted
            ScriptDefinitionsManager.getInstance(project).reloadScriptDefinitionsIfNeeded()
        } else {
            BackgroundTaskUtil.runUnderDisposeAwareIndicator(KotlinPluginDisposable.getInstance(project)) {
                ScriptDefinitionsManager.getInstance(project).reloadScriptDefinitionsIfNeeded()
                ScriptConfigurationManager.getInstance(project).loadPlugins()
            }
        }
    }
}

class ScriptDefinitionsManager(private val project: Project) : LazyScriptDefinitionProvider(), Disposable {
    private val definitionsBySource = mutableMapOf<ScriptDefinitionsSource, List<ScriptDefinition>>()

    @Volatile
    private var definitions: List<ScriptDefinition>? = null
    private val sourcesToReload = mutableSetOf<ScriptDefinitionsSource>()

    private val failedContributorsHashes = HashSet<Int>()

    private val scriptDefinitionsCacheLock = ReentrantLock()
    private val scriptDefinitionsCache = SLRUMap<String, ScriptDefinition>(10, 10)

    // cache service as it's getter is on the hot path
    // it is safe, since both services are in same plugin
    @Volatile
    private var configurations: CompositeScriptConfigurationManager? =
        ScriptConfigurationManager.compositeScriptConfigurationManager(project)

    override fun findDefinition(script: SourceCode): ScriptDefinition? {
        val locationId = script.locationId ?: return null
        if (nonScriptId(locationId)) return null

        configurations?.tryGetScriptDefinitionFast(locationId)?.let { fastPath -> return fastPath }

        if (!isReady()) return null

        scriptDefinitionsCacheLock.withCheckCanceledLock { scriptDefinitionsCache.get(locationId) }?.let { cached -> return cached }

        val definition =
            if (isScratchFile(script)) {
                // Scratch should always have default script definition
                getDefaultDefinition()
            } else {
                super.findDefinition(script) ?: return null
            }

        scriptDefinitionsCacheLock.withCheckCanceledLock {
            scriptDefinitionsCache.put(locationId, definition)
        }

        return definition
    }

    private fun isScratchFile(script: SourceCode): Boolean {
        val virtualFile =
            if (script is VirtualFileScriptSource) script.virtualFile
            else script.locationId?.let { VirtualFileManager.getInstance().findFileByUrl(it) }
        return virtualFile != null && ScratchFileService.getInstance().getRootType(virtualFile) is ScratchRootType
    }

    override fun findScriptDefinition(fileName: String): KotlinScriptDefinition? =
        findDefinition(File(fileName).toScriptSource())?.legacyDefinition

    fun reloadDefinitionsBy(source: ScriptDefinitionsSource) {
        lock.writeWithCheckCanceled {
            if (definitions == null) {
                sourcesToReload.add(source)
                return // not loaded yet
            }
            if (source !in definitionsBySource) error("Unknown script definition source: $source")
        }

        val safeGetDefinitions = source.safeGetDefinitions()
        val updateDefinitionsResult = lock.writeWithCheckCanceled {
            definitionsBySource[source] = safeGetDefinitions

            definitions = definitionsBySource.values.flattenTo(mutableListOf())

            updateDefinitions()
        }
        updateDefinitionsResult?.apply()
    }

    override val currentDefinitions: Sequence<ScriptDefinition>
        get() {
            val scriptingSettings = kotlinScriptingSettingsSafe() ?: return emptySequence()
            return (definitions ?: run {
                reloadScriptDefinitions()
                definitions!!
            }).asSequence().filter { scriptingSettings.isScriptDefinitionEnabled(it) }
        }

    private fun getSources(): List<ScriptDefinitionsSource> {
        @Suppress("DEPRECATION")
        val fromDeprecatedEP = Extensions.getArea(project).getExtensionPoint(ScriptTemplatesProvider.EP_NAME).extensions.toList()
            .map { ScriptTemplatesProviderAdapter(it).asSource() }
        val fromNewEp = ScriptDefinitionContributor.EP_NAME.getPoint(project).extensions.toList()
            .map { it.asSource() }
        return fromNewEp.dropLast(1) + fromDeprecatedEP + fromNewEp.last()
    }

    fun reloadScriptDefinitionsIfNeeded() {
        definitions ?: loadScriptDefinitions()
    }

    fun reloadScriptDefinitions() = loadScriptDefinitions()

    private fun loadScriptDefinitions() {
        if (project.isDisposed) return

        val newDefinitionsBySource = getSources().associateWith { it.safeGetDefinitions() }

        val updateDefinitionsResult = lock.writeWithCheckCanceled {
            definitionsBySource.putAll(newDefinitionsBySource)
            definitions = definitionsBySource.values.flattenTo(mutableListOf())

            updateDefinitions()
        }
        updateDefinitionsResult?.apply()

        lock.writeWithCheckCanceled {
            sourcesToReload.takeIf { it.isNotEmpty() }?.let {
                val copy = ArrayList<ScriptDefinitionsSource>(it)
                it.clear()
                copy
            }
        }?.forEach(::reloadDefinitionsBy)
    }

    fun reorderScriptDefinitions() {
        val scriptingSettings = kotlinScriptingSettingsSafe() ?: return
        val updateDefinitionsResult = lock.writeWithCheckCanceled {
            definitions?.let { list ->
                list.forEach {
                    it.order = scriptingSettings.getScriptDefinitionOrder(it)
                }
                definitions = list.sortedBy(ScriptDefinition::order)

                updateDefinitions()
            }
        }
        updateDefinitionsResult?.apply()
    }

    private fun kotlinScriptingSettingsSafe() = runReadAction {
        if (!project.isDisposed) KotlinScriptingSettings.getInstance(project) else null
    }

    fun getAllDefinitions(): List<ScriptDefinition> = definitions ?: run {
        reloadScriptDefinitions()
        definitions!!
    }

    fun isReady(): Boolean {
        if (definitions == null) return false
        val keys = lock.writeWithCheckCanceled { definitionsBySource.keys }
        return keys.all { source ->
            // TODO: implement another API for readiness checking
            (source as? ScriptDefinitionContributor)?.isReady() != false
        }
    }

    override fun getDefaultDefinition(): ScriptDefinition {
        val standardScriptDefinitionContributor = ScriptDefinitionContributor.find<StandardScriptDefinitionContributor>(project)
            ?: error("StandardScriptDefinitionContributor should be registered is plugin.xml")
        return ScriptDefinition.FromLegacy(getScriptingHostConfiguration(), standardScriptDefinitionContributor.getDefinitions().last())
    }

    private fun updateDefinitions(): UpdateDefinitionsResult? {
        assert(lock.isWriteLocked) { "updateDefinitions should only be called under the write lock" }
        if (project.isDisposed) return null

        val fileTypeManager = FileTypeManager.getInstance()

        val newExtensions = getKnownFilenameExtensions().filter {
            fileTypeManager.getFileTypeByExtension(it) != KotlinFileType.INSTANCE
        }.toSet()

        clearCache()
        scriptDefinitionsCacheLock.withCheckCanceledLock { scriptDefinitionsCache.clear() }

        return UpdateDefinitionsResult(project, newExtensions)
    }

    private data class UpdateDefinitionsResult(val project: Project, val newExtensions: Set<String>) {
        fun apply() {
            if (newExtensions.isNotEmpty()) {
                // Register new file extensions
                ApplicationManager.getApplication().invokeLater {
                    val fileTypeManager = FileTypeManager.getInstance()
                    runWriteAction {
                        newExtensions.forEach {
                            fileTypeManager.associateExtension(KotlinFileType.INSTANCE, it)
                        }
                    }
                }
            }

            // TODO: clear by script type/definition
            ScriptConfigurationManager.getInstance(project).updateScriptDefinitionReferences()
        }
    }

    private fun ScriptDefinitionsSource.safeGetDefinitions(): List<ScriptDefinition> {
        if (!failedContributorsHashes.contains(this@safeGetDefinitions.hashCode())) try {
            return definitions.toList()
        } catch (t: Throwable) {
            if (t is ControlFlowException) throw t
            // reporting failed loading only once
            failedContributorsHashes.add(this@safeGetDefinitions.hashCode())
            // Assuming that direct ClasspathExtractionException is the result of versions mismatch and missing subsystems, e.g. kotlin plugin
            // so, it only results in warning, while other errors are severe misconfigurations, resulting it user-visible error
            if (t.cause is ClasspathExtractionException || t is ClasspathExtractionException) {
                scriptingWarnLog("Cannot load script definitions from $this: ${t.cause?.message ?: t.message}")
            } else {
                scriptingErrorLog("[kts] cannot load script definitions using $this", t)
            }
        }
        return emptyList()
    }

    override fun dispose() {
        clearCache()

        definitionsBySource.clear()
        definitions = null
        failedContributorsHashes.clear()
        scriptDefinitionsCache.clear()
        configurations = null
    }

    companion object {
        fun getInstance(project: Project): ScriptDefinitionsManager =
            project.getServiceSafe<ScriptDefinitionProvider>() as ScriptDefinitionsManager
    }
}

fun loadDefinitionsFromTemplates(
    templateClassNames: List<String>,
    templateClasspath: List<File>,
    baseHostConfiguration: ScriptingHostConfiguration,
    // TODO: need to provide a way to specify this in compiler/repl .. etc
    /*
     * Allows to specify additional jars needed for DependenciesResolver (and not script template).
     * Script template dependencies naturally become (part of) dependencies of the script which is not always desired for resolver dependencies.
     * i.e. gradle resolver may depend on some jars that 'built.gradle.kts' files should not depend on.
     */
    additionalResolverClasspath: List<File> = emptyList(),
    defaultCompilerOptions: Iterable<String> = emptyList()
): List<ScriptDefinition> = loadDefinitionsFromTemplatesByPaths(
    templateClassNames,
    templateClasspath.map(File::toPath),
    baseHostConfiguration,
    additionalResolverClasspath.map(File::toPath),
    defaultCompilerOptions
)

// TODO: consider rewriting to return sequence
fun loadDefinitionsFromTemplatesByPaths(
    templateClassNames: List<String>,
    templateClasspath: List<Path>,
    baseHostConfiguration: ScriptingHostConfiguration,
    // TODO: need to provide a way to specify this in compiler/repl .. etc
    /*
     * Allows to specify additional jars needed for DependenciesResolver (and not script template).
     * Script template dependencies naturally become (part of) dependencies of the script which is not always desired for resolver dependencies.
     * i.e. gradle resolver may depend on some jars that 'built.gradle.kts' files should not depend on.
     */
    additionalResolverClasspath: List<Path> = emptyList(),
    defaultCompilerOptions: Iterable<String> = emptyList()
): List<ScriptDefinition> {
    val classpath = templateClasspath + additionalResolverClasspath
    scriptingInfoLog("Loading script definitions $templateClassNames using classpath: ${classpath.joinToString(File.pathSeparator)}")
    val baseLoader = ScriptDefinitionContributor::class.java.classLoader
    val loader = if (classpath.isEmpty())
        baseLoader
    else
        URLClassLoader(classpath.map { it.toUri().toURL() }.toTypedArray(), baseLoader)

    return templateClassNames.mapNotNull { templateClassName ->
        try {
            // TODO: drop class loading here - it should be handled downstream
            // as a compatibility measure, the asm based reading of annotations should be implemented to filter classes before classloading
            val template = loader.loadClass(templateClassName).kotlin
            val templateClasspathAsFiles = templateClasspath.map(Path::toFile)
            val hostConfiguration = ScriptingHostConfiguration(baseHostConfiguration) {
                configurationDependencies(JvmDependency(templateClasspathAsFiles))
            }

            when {
                template.annotations.firstIsInstanceOrNull<kotlin.script.templates.ScriptTemplateDefinition>() != null -> {
                    ScriptDefinition.FromLegacyTemplate(hostConfiguration, template, templateClasspathAsFiles, defaultCompilerOptions)
                }

                template.annotations.firstIsInstanceOrNull<kotlin.script.experimental.annotations.KotlinScript>() != null -> {
                    ScriptDefinition.FromTemplate(hostConfiguration, template, ScriptDefinition::class, defaultCompilerOptions)
                }

                else -> {
                    scriptingWarnLog("Cannot find a valid script definition annotation on the class $template")
                    null
                }
            }
        } catch (e: ClassNotFoundException) {
            // Assuming that direct ClassNotFoundException is the result of versions mismatch and missing subsystems, e.g. gradle
            // so, it only results in warning, while other errors are severe misconfigurations, resulting it user-visible error
            scriptingWarnLog("Cannot load script definition class $templateClassName")
            null
        } catch (e: Throwable) {
            if (e is ControlFlowException) throw e

            val message = "Cannot load script definition class $templateClassName"
            PluginManagerCore.getPluginByClassName(templateClassName)?.let {
                scriptingErrorLog(message, PluginException(message, e, it))
            } ?: scriptingErrorLog(message, e)
            null
        }
    }
}

@Deprecated("migrating to new configuration refinement: use ScriptDefinitionsSource internally and kotlin.script.experimental.intellij.ScriptDefinitionsProvider as a providing extension point")
interface ScriptDefinitionContributor {

    @Deprecated("migrating to new configuration refinement: drop usages")
    val id: String

    @Deprecated("migrating to new configuration refinement: use ScriptDefinitionsSource instead")
    fun getDefinitions(): List<KotlinScriptDefinition>

    @JvmDefault
    @Deprecated("migrating to new configuration refinement: drop usages")
    fun isReady() = true

    companion object {
        val EP_NAME: ProjectExtensionPointName<ScriptDefinitionContributor> =
            ProjectExtensionPointName("org.jetbrains.kotlin.scriptDefinitionContributor")

        inline fun <reified T> find(project: Project) =
            EP_NAME.getPoint(project).extensionList.filterIsInstance<T>().firstOrNull()
    }
}

@Deprecated("migrating to new configuration refinement: use ScriptDefinitionsSource directly instead")
interface ScriptDefinitionSourceAsContributor : ScriptDefinitionContributor, ScriptDefinitionsSource {

    override fun getDefinitions(): List<KotlinScriptDefinition> = definitions.map { it.legacyDefinition }.toList()
}

@Deprecated("migrating to new configuration refinement: convert all contributors to ScriptDefinitionsSource/ScriptDefinitionsProvider")
class ScriptDefinitionSourceFromContributor(
    val contributor: ScriptDefinitionContributor,
    val hostConfiguration: ScriptingHostConfiguration = defaultJvmScriptingHostConfiguration
) : ScriptDefinitionsSource {
    override val definitions: Sequence<ScriptDefinition>
        get() =
            if (contributor is ScriptDefinitionsSource) contributor.definitions
            else contributor.getDefinitions().asSequence().map { ScriptDefinition.FromLegacy(hostConfiguration, it) }

    override fun equals(other: Any?): Boolean {
        return contributor.id == (other as? ScriptDefinitionSourceFromContributor)?.contributor?.id
    }

    override fun hashCode(): Int {
        return contributor.id.hashCode()
    }
}

fun ScriptDefinitionContributor.asSource(): ScriptDefinitionsSource =
    if (this is ScriptDefinitionsSource) this
    else ScriptDefinitionSourceFromContributor(this)

class StandardScriptDefinitionContributor(val project: Project) : ScriptDefinitionContributor {
    private val standardIdeScriptDefinition = StandardIdeScriptDefinition(project)

    override fun getDefinitions() = listOf(standardIdeScriptDefinition)

    override val id: String = "StandardKotlinScript"
}


class StandardIdeScriptDefinition internal constructor(project: Project) : KotlinScriptDefinition(ScriptTemplateWithArgs::class) {
    override val dependencyResolver = BundledKotlinScriptDependenciesResolver(project)
}

class BundledKotlinScriptDependenciesResolver(private val project: Project) : DependenciesResolver {
    override fun resolve(
        scriptContents: ScriptContents,
        environment: Environment
    ): DependenciesResolver.ResolveResult {
        val virtualFile = scriptContents.file?.let { VfsUtil.findFileByIoFile(it, true) }

        val javaHome = getScriptSDK(project, virtualFile)

        var classpath = with(KotlinArtifacts.instance) {
            listOf(kotlinReflect, kotlinStdlib, kotlinScriptRuntime)
        }
        if (ScratchFileService.getInstance().getRootType(virtualFile) is IdeConsoleRootType) {
            classpath = scriptCompilationClasspathFromContextOrStdlib(wholeClasspath = true) + classpath
        }

        return ScriptDependencies(javaHome = javaHome?.let(::File), classpath = classpath).asSuccess()
    }

    private fun getScriptSDK(project: Project, virtualFile: VirtualFile?): String? {
        if (virtualFile != null) {
            val dependentModuleSourceInfo = getScriptRelatedModuleInfo(project, virtualFile)
            val sdk = dependentModuleSourceInfo?.dependencies()?.filterIsInstance<SdkInfo>()?.singleOrNull()?.sdk
            if (sdk != null) {
                return sdk.homePath
            }
        }

        val jdk = ProjectRootManager.getInstance(project).projectSdk
            ?: getProjectJdkTableSafe().allJdks.firstOrNull { sdk -> sdk.sdkType is JavaSdk }
            ?: PathUtilEx.getAnyJdk(project)
        return jdk?.homePath
    }
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script

import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.ide.script.IdeConsoleRootType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.ex.PathUtilEx
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.containers.SLRUMap
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleInfoProvider
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.idea.base.util.CheckCanceledLock
import org.jetbrains.kotlin.idea.base.util.writeWithCheckCanceled
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.script.ScriptTemplatesProvider
import org.jetbrains.kotlin.scripting.definitions.*
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.flattenTo
import java.io.File
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock
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
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContextOrStdlib
import kotlin.script.templates.standard.ScriptTemplateWithArgs

internal class LoadScriptDefinitionsStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) : Unit = blockingContext {
        if (isUnitTestMode()) {
            // In tests definitions are loaded synchronously because they are needed to analyze script
            // In IDE script won't be highlighted before all definitions are loaded, then the highlighting will be restarted
            ScriptDefinitionsManager.getInstance(project).reloadScriptDefinitionsIfNeeded()
        } else {
            ScriptDefinitionsManager.getInstance(project).reloadScriptDefinitionsIfNeeded()
            ScriptConfigurationManager.getInstance(project).loadPlugins()
        }
    }
}

class ScriptDefinitionsManager(private val project: Project) : LazyScriptDefinitionProvider(), Disposable {
    private val definitionsLock = ReentrantReadWriteLock()
    private val definitionsBySource = mutableMapOf<ScriptDefinitionsSource, List<ScriptDefinition>>()

    @Volatile
    private var definitions: List<ScriptDefinition>? = null
    private val sourcesToReload = mutableSetOf<ScriptDefinitionsSource>()

    private val failedContributorsHashes = HashSet<Int>()

    private val scriptDefinitionsCacheLock = CheckCanceledLock()
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
        scriptDefinitionsCacheLock.withLock { scriptDefinitionsCache.get(locationId) }?.let { cached -> return cached }

        val definition =
            if (isScratchFile(script)) {
                // Scratch should always have default script definition
                getDefaultDefinition()
            } else {
                if (definitions == null) return DeferredScriptDefinition(script, this)
                super.findDefinition(script) ?: return null
            }

        scriptDefinitionsCacheLock.withLock {
            scriptDefinitionsCache.put(locationId, definition)
        }

        return definition
    }

    internal fun DeferredScriptDefinition.valueIfAny(): ScriptDefinition? {
        if (definitions == null) return null

        val locationId = requireNotNull(scriptCode.locationId)
        configurations?.tryGetScriptDefinitionFast(locationId)?.let { fastPath -> return fastPath }
        scriptDefinitionsCacheLock.withLock { scriptDefinitionsCache.get(locationId) }?.let { cached -> return cached }
        return super.findDefinition(scriptCode)
    }

    private fun isScratchFile(script: SourceCode): Boolean {
        val virtualFile =
            if (script is VirtualFileScriptSource) script.virtualFile
            else script.locationId?.let { VirtualFileManager.getInstance().findFileByUrl(it) }
        return virtualFile != null && ScratchFileService.getInstance().getRootType(virtualFile) is ScratchRootType
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun findScriptDefinition(fileName: String): KotlinScriptDefinition? {
        @Suppress("DEPRECATION")
        return findDefinition(File(fileName).toScriptSource())?.legacyDefinition
    }

    fun reloadDefinitionsBy(source: ScriptDefinitionsSource) {
        definitionsLock.writeWithCheckCanceled {
            if (definitions == null) {
                sourcesToReload.add(source)
                return // not loaded yet
            }
            if (source !in definitionsBySource) error("Unknown script definition source: $source")
        }

        val safeGetDefinitions = source.safeGetDefinitions()
        val updateDefinitionsResult = run {
            definitionsLock.writeWithCheckCanceled {
                definitionsBySource[source] = safeGetDefinitions
                definitions = definitionsBySource.values.flattenTo(mutableListOf())
            }
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
        val fromDeprecatedEP = project.extensionArea.getExtensionPoint(ScriptTemplatesProvider.EP_NAME).extensions.toList()
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

        val updateDefinitionsResult = run {
            definitionsLock.writeWithCheckCanceled {
                definitionsBySource.putAll(newDefinitionsBySource)
                definitions = definitionsBySource.values.flattenTo(mutableListOf())
            }
            updateDefinitions()
        }
        updateDefinitionsResult?.apply()

        definitionsLock.writeWithCheckCanceled {
            sourcesToReload.takeIf { it.isNotEmpty() }?.let {
                val copy = ArrayList<ScriptDefinitionsSource>(it)
                it.clear()
                copy
            }
        }?.forEach(::reloadDefinitionsBy)
    }

    fun reorderScriptDefinitions() {
        val scriptingSettings = kotlinScriptingSettingsSafe() ?: return
        val updateDefinitionsResult = run {
            definitionsLock.writeWithCheckCanceled {
                definitions?.let { list ->
                    list.forEach {
                        it.order = scriptingSettings.getScriptDefinitionOrder(it)
                    }
                    definitions = list.sortedBy(ScriptDefinition::order)
                }
            }
            updateDefinitions()
        }
        updateDefinitionsResult?.apply()
    }

    private fun kotlinScriptingSettingsSafe(): KotlinScriptingSettings? {
        assert(!definitionsLock.isWriteLockedByCurrentThread) { "kotlinScriptingSettingsSafe should be called out if the write lock to avoid deadlocks" }
        return runReadAction {
            if (!project.isDisposed) KotlinScriptingSettings.getInstance(project) else null
        }
    }

    fun getAllDefinitions(): List<ScriptDefinition> = definitions ?: run {
        reloadScriptDefinitions()
        definitions!!
    }

    fun isReady(): Boolean {
        if (definitions == null) return false
        val keys = definitionsLock.writeWithCheckCanceled { definitionsBySource.keys }
        return keys.all { source ->
            // TODO: implement another API for readiness checking
            (source as? ScriptDefinitionContributor)?.isReady() != false
        }
    }

    override fun getDefaultDefinition(): ScriptDefinition {
        val standardScriptDefinitionContributor = ScriptDefinitionContributor.find<StandardScriptDefinitionContributor>(project)
            ?: error("StandardScriptDefinitionContributor should be registered in plugin.xml")
        return ScriptDefinition.FromLegacy(getScriptingHostConfiguration(), standardScriptDefinitionContributor.getDefinitions().last())
    }

    private fun updateDefinitions(): UpdateDefinitionsResult? {
        assert(!definitionsLock.isWriteLockedByCurrentThread) { "updateDefinitions should be called out the write lock" }
        if (project.isDisposed) return null

        val fileTypeManager = FileTypeManager.getInstance()

        val newExtensions = getKnownFilenameExtensions().toSet().filter {
            val fileTypeByExtension = fileTypeManager.getFileTypeByFileName("xxx.$it")
            val notKnown = fileTypeByExtension != KotlinFileType.INSTANCE
            if (notKnown) {
                scriptingWarnLog("extension $it file type [${fileTypeByExtension.name}] is not registered as ${KotlinFileType.INSTANCE.name}")
            }
            notKnown
        }.toSet()

        clearCache()
        scriptDefinitionsCacheLock.withLock { scriptDefinitionsCache.clear() }

        return UpdateDefinitionsResult(project, newExtensions)
    }

    private data class UpdateDefinitionsResult(val project: Project, val newExtensions: Set<String>) {
        fun apply() {
            if (newExtensions.isNotEmpty()) {
                scriptingWarnLog("extensions ${newExtensions} is about to be registered as ${KotlinFileType.INSTANCE.name}")
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
            scriptingErrorLog("Cannot load script definitions from $this: ${t.cause?.message ?: t.message}", t)
        }
        return emptyList()
    }

    override fun dispose() {
        super.dispose()

        clearCache()

        definitionsBySource.clear()
        definitions = null
        failedContributorsHashes.clear()
        scriptDefinitionsCache.clear()
        configurations = null
    }

    companion object {
        fun getInstance(project: Project): ScriptDefinitionsManager =
            project.service<ScriptDefinitionProvider>() as ScriptDefinitionsManager
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
        UrlClassLoader.build().files(classpath).parent(baseLoader).get()

    return templateClassNames.mapNotNull { templateClassName ->
        try {
            // TODO: drop class loading here - it should be handled downstream
            // as a compatibility measure, the asm based reading of annotations should be implemented to filter classes before classloading
            val template = loader.loadClass(templateClassName).kotlin
            // do not use `Path::toFile` here as it might break the path format of non-local file system
            val templateClasspathAsFiles = templateClasspath.map { File(it.toString()) }
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
            scriptingWarnLog("Cannot load script definition class $templateClassName", e)
            null
        } catch (e: Throwable) {
            if (e is ControlFlowException) {
                throw e
            }

            val message = "Cannot load script definition class $templateClassName"
            PluginManager.getPluginByClassNameAsNoAccessToClass(templateClassName)?.let {
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
  private val hostConfiguration: ScriptingHostConfiguration = defaultJvmScriptingHostConfiguration
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

        val classpath = buildList {
            if (ScratchFileService.getInstance().getRootType(virtualFile) is IdeConsoleRootType) {
                addAll(scriptCompilationClasspathFromContextOrStdlib(wholeClasspath = true))
            }
            add(KotlinArtifacts.kotlinReflect)
            add(KotlinArtifacts.kotlinStdlib)
            add(KotlinArtifacts.kotlinScriptRuntime)
        }

        return ScriptDependencies(javaHome = javaHome?.let(::File), classpath = classpath).asSuccess()
    }

    private fun getScriptSDK(project: Project, virtualFile: VirtualFile?): String? {
        if (virtualFile != null) {
            for (result in ModuleInfoProvider.getInstance(project).collect(virtualFile)) {
                val moduleInfo = result.getOrNull() ?: break
                val sdk = moduleInfo.dependencies().asSequence().filterIsInstance<SdkInfo>().singleOrNull()?.sdk ?: continue
                return sdk.homePath
            }
        }

        val jdk = ProjectRootManager.getInstance(project).projectSdk
            ?: runReadAction { ProjectJdkTable.getInstance() }.allJdks
                .firstOrNull { sdk -> sdk.sdkType is JavaSdk }
            ?: PathUtilEx.getAnyJdk(project)
        return jdk?.homePath
    }
}
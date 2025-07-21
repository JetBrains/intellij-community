// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.uast.UastModificationTracker
import com.intellij.util.containers.SLRUCache
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.analyzer.ResolverForProject.Companion.resolverForLibrariesName
import org.jetbrains.kotlin.analyzer.ResolverForProject.Companion.resolverForModulesName
import org.jetbrains.kotlin.analyzer.ResolverForProject.Companion.resolverForScriptDependenciesName
import org.jetbrains.kotlin.analyzer.ResolverForProject.Companion.resolverForSdkName
import org.jetbrains.kotlin.analyzer.ResolverForProject.Companion.resolverForSpecialInfoName
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.caches.resolve.PlatformAnalysisSettings
import org.jetbrains.kotlin.context.GlobalContext
import org.jetbrains.kotlin.context.GlobalContextImpl
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.useCompositeAnalysis
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibrarySourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.NotUnderContentRootModuleInfo
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.caches.project.getDependentModules
import org.jetbrains.kotlin.idea.caches.project.isLibraryClasses
import org.jetbrains.kotlin.idea.caches.resolve.util.GlobalFacadeModuleFilters
import org.jetbrains.kotlin.idea.caches.resolve.util.contextWithCompositeExceptionTracker
import org.jetbrains.kotlin.idea.caches.trackers.outOfBlockModificationCount
import org.jetbrains.kotlin.idea.core.script.v1.ScriptAdditionalIdeaDependenciesProvider
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesInfo
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesSourceInfo
import org.jetbrains.kotlin.idea.core.script.v1.ScriptModuleInfo
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.contains
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.diagnostics.KotlinSuppressCache
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong

internal val LOG = Logger.getInstance(KotlinCacheService::class.java)

data class PlatformAnalysisSettingsImpl(
    val platform: TargetPlatform,
    val sdk: Sdk?
) : PlatformAnalysisSettings

object CompositeAnalysisSettings : PlatformAnalysisSettings

fun createPlatformAnalysisSettings(
    project: Project,
    platform: TargetPlatform,
    sdk: Sdk?
): PlatformAnalysisSettings {
    return if (project.useCompositeAnalysis) {
        CompositeAnalysisSettings
    } else {
        PlatformAnalysisSettingsImpl(platform, sdk)
    }
}

class KotlinCacheServiceImpl(val project: Project) : KotlinCacheService {
    override fun getResolutionFacade(element: KtElement): ResolutionFacade {
        val file = element.fileForElement()
        return CachedValuesManager.getCachedValue(file) {
            val settings = file.moduleInfo.platformSettings(file.platform)
            CachedValueProvider.Result(
                getFacadeToAnalyzeFile(file, settings),
                UastModificationTracker.getInstance(project),
                ProjectRootModificationTracker.getInstance(project),
            )
        }
    }

    override fun getResolutionFacade(elements: List<KtElement>): ResolutionFacade {
        val files = getFilesForElements(elements)

        if (files.size == 1) return getResolutionFacade(files.single())
        val platform = files.first().platform

        // it is quite suspicious that we take first().moduleInfo, while there might be multiple files of different kinds (e.g.
        // some might be "special", see case chasing in [getFacadeToAnalyzeFiles] later
        val settings = files.first().moduleInfo.platformSettings(platform)
        return getFacadeToAnalyzeFiles(files, settings)
    }

    // Implementation note: currently, it provides platform-specific view on common sources via plain creation of
    // separate GlobalFacade even when CompositeAnalysis is enabled.
    //
    // Because GlobalFacade retains a lot of memory and is cached per-platform, calling this function with non-simple TargetPlatforms
    // (e.g. with {JVM, Android}, {JVM_1.6, JVM_1.8}, etc.) might lead to explosive growth of memory consumption, so such calls are
    // logged as errors currently and require immediate attention.
    override fun getResolutionFacadeWithForcedPlatform(elements: List<KtElement>, platform: TargetPlatform): ResolutionFacade {
        val files = getFilesForElements(elements)
        val moduleInfo = files.first().moduleInfo
        val settings = PlatformAnalysisSettingsImpl(
            platform,
            moduleInfo.sdk()
        )

        if (!canGetFacadeWithForcedPlatform(elements, files, moduleInfo, platform)) {
            // Fallback to facade without forced platform
            return getResolutionFacade(elements)
        }

        // Note that there's no need to switch on project.useCompositeAnalysis
        // - For SEPARATE analysis, using 'PlatformAnalysisSettingsImpl' is totally OK, and actually that's what we'd create normally
        //
        // - For COMPOSITE analysis, we intentionally want to use 'PlatformAnalysisSettingsImpl' to re-analyze code in separate
        //   platform-specific facade, even though normally we'd create CompositeAnalysisSettings.
        //   Some branches in [getFacadeToAnalyzeFile] in such case will be effectively dead due to [canGetFacadeWithForcedPlatform]
        //   (e.g. everything script-related), but, for example, special-files are still needed, so one can't just skip straight to
        //   [getResolutionFacadeByModuleInfoAndSettings] instead
        return getFacadeToAnalyzeFiles(files, settings)
    }

    private fun canGetFacadeWithForcedPlatform(
        elements: List<KtElement>,
        files: List<KtFile>,
        moduleInfo: IdeaModuleInfo,
        platform: TargetPlatform
    ): Boolean {
        val specialFiles = files.filterNotInProjectSource(moduleInfo)
        val scripts = specialFiles.filterScripts()

        return when {
            platform.size > 1 -> {
                LOG.error(
                    "Getting resolution facade with non-trivial platform $platform is strongly discouraged,\n" +
                            "as it can lead to heavy memory consumption. Facade with non-forced platform will be used instead."
                )
                false
            }

            moduleInfo is ScriptDependenciesInfo || moduleInfo is ScriptDependenciesSourceInfo -> {
                LOG.error(
                    "Getting resolution facade for ScriptDependencies is not supported\n" +
                            "Requested elements: $elements\n" +
                            "Files for requested elements: $files\n" +
                            "Module info for the first file: $moduleInfo"
                )
                false
            }

            scripts.isNotEmpty() -> {

                LOG.error(
                    "Getting resolution facade with forced platform is not supported for scripts\n" +
                            "Requested elements: $elements\n" +
                            "Files for requested elements: $files\n" +
                            "Among them, following are scripts: $scripts"
                )
                false
            }

            else -> true
        }
    }

    private fun getFilesForElements(elements: List<KtElement>): List<KtFile> {
        return elements.map {
            it.fileForElement()
        }.distinct()
    }

    private fun KtElement.fileForElement() = try {
        // in theory `containingKtFile` is `@NotNull` but in practice EA-114080
        @Suppress("USELESS_ELVIS")
        containingKtFile ?: throw IllegalStateException("containingKtFile was null for $this of ${this.javaClass}")
    } catch (e: Exception) {
        if (e is ControlFlowException) throw e
        throw KotlinExceptionWithAttachments("Couldn't get containingKtFile for ktElement", e)
            .withPsiAttachment("element", this)
            .withPsiAttachment("file", this.containingFile)
            .withAttachment("original", e.message)
    }

    override fun getSuppressionCache(): KotlinSuppressCache = kotlinSuppressCache.value

    private val globalFacadesPerPlatformAndSdk: SLRUCache<PlatformAnalysisSettings, GlobalFacade> =
        SLRUCache.slruCache(2 * 3 * 2, 2 * 3 * 2) { GlobalFacade(it) }

    private val facadeForScriptDependenciesForProject =
        lazy { createFacadeForScriptDependencies(ScriptDependenciesInfo.ForProject(project)) }

    private fun createFacadeForScriptDependencies(
        dependenciesModuleInfo: ScriptDependenciesInfo
    ): ProjectResolutionFacade {
        val sdk = dependenciesModuleInfo.sdk
        val platform = JvmPlatforms.defaultJvmPlatform // TODO: Js scripts?
        val settings = createPlatformAnalysisSettings(project, platform, sdk)

        val dependenciesForScriptDependencies = listOf(
            JavaLibraryModificationTracker.getInstance(project),
            ScriptDependenciesModificationTracker.getInstance(project)
        )

        val scriptFile = (dependenciesModuleInfo as? ScriptDependenciesInfo.ForFile)?.scriptFile
        val relatedModules = scriptFile?.let { ScriptAdditionalIdeaDependenciesProvider.getRelatedModules(it, project) }
        val globalFacade =
            if (relatedModules?.isNotEmpty() == true) {
                facadeForModules(settings)
            } else {
                getOrBuildGlobalFacade(settings).facadeForSdk
            }

        val globalContext = globalFacade.globalContext.contextWithCompositeExceptionTracker(project, "facadeForScriptDependencies")
        return ProjectResolutionFacade(
            "facadeForScriptDependencies",
            resolverForScriptDependenciesName,
            project, globalContext, settings,
            reuseDataFrom = globalFacade,
            allModules = dependenciesModuleInfo.dependencies(),
            //TODO: provide correct trackers
            dependencies = dependenciesForScriptDependencies,
            moduleFilter = { it == dependenciesModuleInfo },
            invalidateOnOOCB = false
        )
    }

    private inner class GlobalFacade(settings: PlatformAnalysisSettings) {
        private val sdkContext = GlobalContext(resolverForSdkName)
        private val moduleFilters = GlobalFacadeModuleFilters(project)
        val facadeForSdk = ProjectResolutionFacade(
            "facadeForSdk", "$resolverForSdkName with settings=$settings",
            project, sdkContext, settings,
            moduleFilter = moduleFilters::sdkFacadeFilter,
            dependencies = listOf(
                JavaLibraryModificationTracker.getInstance(project),
                ProjectRootModificationTracker.getInstance(project)
            ),
            invalidateOnOOCB = false,
            reuseDataFrom = null
        )

        private val librariesContext = sdkContext.contextWithCompositeExceptionTracker(project, resolverForLibrariesName)
        val facadeForLibraries = ProjectResolutionFacade(
            "facadeForLibraries", "$resolverForLibrariesName with settings=$settings",
            project, librariesContext, settings,
            reuseDataFrom = facadeForSdk,
            moduleFilter = moduleFilters::libraryFacadeFilter,
            invalidateOnOOCB = false,
            dependencies = listOf(
                JavaLibraryModificationTracker.getInstance(project),
                ProjectRootModificationTracker.getInstance(project)
            )
        )

        private val modulesContext = librariesContext.contextWithCompositeExceptionTracker(project, resolverForModulesName)
        val facadeForModules = ProjectResolutionFacade(
            "facadeForModules", "$resolverForModulesName with settings=$settings",
            project, modulesContext, settings,
            reuseDataFrom = facadeForLibraries,
            moduleFilter = moduleFilters::moduleFacadeFilter,
            dependencies = listOf(ProjectRootModificationTracker.getInstance(project)),
            invalidateOnOOCB = true
        )
    }

    private fun IdeaModuleInfo.platformSettings(targetPlatform: TargetPlatform) = createPlatformAnalysisSettings(
        this@KotlinCacheServiceImpl.project, targetPlatform, sdk()
    )

    private fun facadeForModules(settings: PlatformAnalysisSettings) =
        getOrBuildGlobalFacade(settings).facadeForModules

    private fun librariesFacade(settings: PlatformAnalysisSettings) =
        getOrBuildGlobalFacade(settings).facadeForLibraries

    @Synchronized
    private fun getOrBuildGlobalFacade(settings: PlatformAnalysisSettings) =
        globalFacadesPerPlatformAndSdk[settings]

    // explicitSettings allows to override the "innate" settings of the files' moduleInfo
    // This can be useful, if the module is common, but we want to create a facade to
    // analyze that module from the platform (e.g. JVM) point of view
    private fun createFacadeForFilesWithSpecialModuleInfo(
        files: Set<KtFile>,
        explicitSettings: PlatformAnalysisSettings? = null
    ): ProjectResolutionFacade {
        // we assume that all files come from the same module
        val targetPlatform = files.map { it.platform }.toSet().single()
        val specialModuleInfo = files.map { it.moduleInfo }.toSet().single()
        val settings = explicitSettings ?: specialModuleInfo.platformSettings(specialModuleInfo.platform)

        // Dummy files created e.g. by J2K do not receive events.
        val dependencyTrackerForSyntheticFileCache = if (files.all { it.originalFile != it }) {
            ModificationTracker { files.sumByLong { it.outOfBlockModificationCount } }
        } else ModificationTracker { files.sumByLong { it.modificationStamp } }

        val resolverDebugName =
            "$resolverForSpecialInfoName $specialModuleInfo for files ${files.joinToString { it.name }} for platform $targetPlatform"

        fun makeProjectResolutionFacade(
            debugName: String,
            globalContext: GlobalContextImpl,
            reuseDataFrom: ProjectResolutionFacade? = null,
            moduleFilter: (IdeaModuleInfo) -> Boolean = { true },
            allModules: Collection<IdeaModuleInfo>? = null
        ): ProjectResolutionFacade {
            return ProjectResolutionFacade(
                debugName,
                resolverDebugName,
                project,
                globalContext,
                settings,
                syntheticFiles = files,
                reuseDataFrom = reuseDataFrom,
                moduleFilter = moduleFilter,
                dependencies = listOf(
                    dependencyTrackerForSyntheticFileCache,
                    ProjectRootModificationTracker.getInstance(project)
                ),
                invalidateOnOOCB = true,
                allModules = allModules
            )
        }

        return when {
            specialModuleInfo is ModuleSourceInfo -> {
                val dependentModules = specialModuleInfo.getDependentModules()
                val modulesFacade = facadeForModules(settings)
                val globalContext =
                    modulesFacade.globalContext.contextWithCompositeExceptionTracker(
                        project,
                        "facadeForSpecialModuleInfo (ModuleSourceInfo)"
                    )
                makeProjectResolutionFacade(
                    "facadeForSpecialModuleInfo (ModuleSourceInfo)",
                    globalContext,
                    reuseDataFrom = modulesFacade,
                    moduleFilter = { it in dependentModules }
                )
            }

            specialModuleInfo is ScriptModuleInfo -> {
                val facadeForScriptDependencies = createFacadeForScriptDependencies(
                    ScriptDependenciesInfo.ForFile(project, specialModuleInfo.scriptFile, specialModuleInfo.scriptDefinition)
                )
                val globalContext = facadeForScriptDependencies.globalContext.contextWithCompositeExceptionTracker(
                    project,
                    "facadeForSpecialModuleInfo (ScriptModuleInfo)"
                )
                makeProjectResolutionFacade(
                    "facadeForSpecialModuleInfo (ScriptModuleInfo)",
                    globalContext,
                    reuseDataFrom = facadeForScriptDependencies,
                    allModules = specialModuleInfo.dependencies(),
                    moduleFilter = { it == specialModuleInfo }
                )
            }

            specialModuleInfo is ScriptDependenciesInfo -> facadeForScriptDependenciesForProject.value
            specialModuleInfo is ScriptDependenciesSourceInfo -> {
                val globalContext =
                    facadeForScriptDependenciesForProject.value.globalContext.contextWithCompositeExceptionTracker(
                        project,
                        "facadeForSpecialModuleInfo (ScriptDependenciesSourceInfo)"
                    )
                makeProjectResolutionFacade(
                    "facadeForSpecialModuleInfo (ScriptDependenciesSourceInfo)",
                    globalContext,
                    reuseDataFrom = facadeForScriptDependenciesForProject.value,
                    allModules = specialModuleInfo.dependencies(),
                    moduleFilter = { it == specialModuleInfo }
                )
            }

            specialModuleInfo is LibrarySourceInfo || specialModuleInfo is NotUnderContentRootModuleInfo -> {
                val librariesFacade = librariesFacade(settings)
                val debugName = "facadeForSpecialModuleInfo (LibrarySourceInfo or NotUnderContentRootModuleInfo)"
                val globalContext = librariesFacade.globalContext.contextWithCompositeExceptionTracker(project, debugName)
                makeProjectResolutionFacade(
                    debugName,
                    globalContext,
                    reuseDataFrom = librariesFacade,
                    moduleFilter = { it == specialModuleInfo }
                )
            }

            specialModuleInfo.isLibraryClasses() -> {
                //NOTE: this code should not be called for sdk or library classes
                // currently the only known scenario is when we cannot determine that file is a library source
                // (file under both classes and sources root)
                LOG.warn("Creating cache with synthetic files ($files) in classes of library $specialModuleInfo")
                val globalContext = GlobalContext("facadeForSpecialModuleInfo for file under both classes and root")
                makeProjectResolutionFacade(
                    "facadeForSpecialModuleInfo for file under both classes and root",
                    globalContext
                )
            }

            else -> throw IllegalStateException("Unknown IdeaModuleInfo ${specialModuleInfo::class.java}")
        }
    }

    private val kotlinSuppressCache: CachedValue<KotlinSuppressCache> = CachedValuesManager.getManager(project).createCachedValue(
        {
            CachedValueProvider.Result(
                object : KotlinSuppressCache(project) {
                    override fun getSuppressionAnnotations(annotated: PsiElement): List<AnnotationDescriptor> {
                        if (annotated !is KtAnnotated) return emptyList()
                        if (!KotlinPsiHeuristics.hasSuppressAnnotation(annotated)) {
                            // Avoid running resolve heuristics
                            return emptyList()
                        }

                        val context = when (annotated) {
                            is KtFile -> annotated.fileAnnotationList?.safeAnalyze(BodyResolveMode.PARTIAL_NO_ADDITIONAL)
                            is KtModifierListOwner -> annotated.modifierList?.safeAnalyze(BodyResolveMode.PARTIAL_NO_ADDITIONAL)
                            else -> annotated.safeAnalyze(BodyResolveMode.PARTIAL_NO_ADDITIONAL)
                        } ?: return emptyList()

                        val annotatedDescriptor = context.get(BindingContext.DECLARATION_TO_DESCRIPTOR, annotated)
                        if (annotatedDescriptor != null) {
                            return annotatedDescriptor.annotations.toList()
                        }

                        return annotated.annotationEntries.mapNotNull { context.get(BindingContext.ANNOTATION, it) }
                    }
                },
                JavaLibraryModificationTracker.getInstance(project),
                UastModificationTracker.getInstance(project)
            )
        },
        false
    )

    private val specialFilesCacheProvider = CachedValueProvider {
        // NOTE: computations inside createFacadeForFilesWithSpecialModuleInfo depend on project root structure
        // so we additionally drop the whole slru cache on change
        CachedValueProvider.Result(
            SLRUCache.slruCache<Pair<Set<KtFile>, PlatformAnalysisSettings>, ProjectResolutionFacade>(2, 3) {
                createFacadeForFilesWithSpecialModuleInfo(it.first, it.second)
            },
            JavaLibraryModificationTracker.getInstance(project),
            ProjectRootModificationTracker.getInstance(project)
        )
    }

    private fun getFacadeForSpecialFiles(files: Set<KtFile>, settings: PlatformAnalysisSettings): ProjectResolutionFacade {
        val cachedValue: SLRUCache<Pair<Set<KtFile>, PlatformAnalysisSettings>, ProjectResolutionFacade> =
            CachedValuesManager.getManager(project).getCachedValue(project, specialFilesCacheProvider)

        // In Upsource, we create multiple instances of KotlinCacheService, which all access the same CachedValue instance (UP-8046)
        // This is so because class name of provider is used as a key when fetching cached value, see CachedValueManager.getKeyForClass.
        // To avoid race conditions, we can't use any local lock to access the cached value contents.
        return cachedValue.getOrCreateValue(files to settings)
    }

    private val scriptsCacheProvider = CachedValueProvider {
        CachedValueProvider.Result(
            SLRUCache.slruCache<Set<KtFile>, ProjectResolutionFacade>(10, 5, ::createFacadeForFilesWithSpecialModuleInfo),
            JavaLibraryModificationTracker.getInstance(project),
            ProjectRootModificationTracker.getInstance(project),
            ScriptDependenciesModificationTracker.getInstance(project)
        )
    }

    private fun getFacadeForScripts(files: Set<KtFile>): ProjectResolutionFacade {
        val cachedValue: SLRUCache<Set<KtFile>, ProjectResolutionFacade> =
            CachedValuesManager.getManager(project).getCachedValue(project, scriptsCacheProvider)

        return cachedValue.getOrCreateValue(files)
    }

    private fun <K, V> SLRUCache<K, V>.getOrCreateValue(key: K): V =
        synchronized(this) {
            this.getIfCached(key)
        } ?: run {
            // do actual value calculation out of any locks
            // trade-off: several instances could be created, but only one would be used
            val newValue = this.createValue(key)
            synchronized(this) {
                val cached = this.getIfCached(key)
                cached ?: run {
                    this.put(key, newValue)
                    newValue
                }
            }
        }

    private fun getFacadeToAnalyzeFiles(files: Collection<KtFile>, settings: PlatformAnalysisSettings): ResolutionFacade {
        val moduleInfo = files.first().moduleInfo
        val specialFiles = files.filterNotInProjectSource(moduleInfo)
        val scripts = specialFiles.filterScripts()
        if (scripts.isNotEmpty()) {
            val projectFacade = getFacadeForScripts(scripts)
            return ModuleResolutionFacadeImpl(projectFacade, moduleInfo).createdFor(scripts, moduleInfo, settings)
        }

        if (specialFiles.isNotEmpty()) {
            val projectFacade = getFacadeForSpecialFiles(specialFiles, settings)
            return ModuleResolutionFacadeImpl(projectFacade, moduleInfo).createdFor(specialFiles, moduleInfo, settings)
        }

        return getResolutionFacadeByModuleInfoAndSettings(moduleInfo, settings).createdFor(emptyList(), moduleInfo, settings)
    }

    private fun getFacadeToAnalyzeFile(file: KtFile, settings: PlatformAnalysisSettings): ResolutionFacade {
        val moduleInfo = file.moduleInfo
        val specialFile = filterNotInProjectSource(file, moduleInfo)

        specialFile?.filterScript()?.let { script ->
            val scripts = setOf(script)
            val projectFacade = getFacadeForScripts(scripts)
            return ModuleResolutionFacadeImpl(projectFacade, moduleInfo).createdFor(scripts, moduleInfo, settings)
        }

        if (specialFile != null) {
            val specialFiles = setOf(specialFile)
            val projectFacade = getFacadeForSpecialFiles(specialFiles, settings)
            return ModuleResolutionFacadeImpl(projectFacade, moduleInfo).createdFor(specialFiles, moduleInfo, settings)
        }

        return getResolutionFacadeByModuleInfoAndSettings(moduleInfo, settings).createdFor(emptyList(), moduleInfo, settings)
    }

    override fun getResolutionFacadeByFile(file: PsiFile, platform: TargetPlatform): ResolutionFacade? {
        if (!RootKindFilter.everything.matches(file)) {
            return null
        }

        assert(file !is PsiCodeFragment)

        val moduleInfo = file.moduleInfo
        return getResolutionFacadeByModuleInfo(moduleInfo, platform)
    }

    override fun getResolutionFacadeByModuleInfo(moduleInfo: IdeaModuleInfo, platform: TargetPlatform): ResolutionFacade {
        val settings = moduleInfo.platformSettings(platform)
        return getResolutionFacadeByModuleInfoAndSettings(moduleInfo, settings)
    }

    private fun getResolutionFacadeByModuleInfoAndSettings(
        moduleInfo: IdeaModuleInfo,
        settings: PlatformAnalysisSettings
    ): ResolutionFacade {
        val projectFacade = when (moduleInfo) {
            is ScriptDependenciesInfo.ForProject,
            is ScriptDependenciesSourceInfo.ForProject -> facadeForScriptDependenciesForProject.value

            is ScriptDependenciesInfo.ForFile -> createFacadeForScriptDependencies(moduleInfo)
            else -> facadeForModules(settings)
        }
        return ModuleResolutionFacadeImpl(projectFacade, moduleInfo)
    }

    override fun getResolutionFacadeByModuleInfo(moduleInfo: ModuleInfo, platform: TargetPlatform): ResolutionFacade? =
        (moduleInfo as? IdeaModuleInfo)?.let { getResolutionFacadeByModuleInfo(it, platform) }

    override fun getResolutionFacadeByModuleInfo(moduleInfo: ModuleInfo, settings: PlatformAnalysisSettings): ResolutionFacade? {
        val ideaModuleInfo = moduleInfo as? IdeaModuleInfo ?: return null
        return getResolutionFacadeByModuleInfoAndSettings(ideaModuleInfo, settings)
    }

    private fun Collection<KtFile>.filterNotInProjectSource(moduleInfo: IdeaModuleInfo): Set<KtFile> =
        mapNotNullTo(mutableSetOf()) { filterNotInProjectSource(it, moduleInfo) }

    private fun filterNotInProjectSource(file: KtFile, moduleInfo: IdeaModuleInfo): KtFile? {
        val fileToAnalyze = when (file) {
            is KtCodeFragment -> file.getContextFile()
            else -> file
        }

        if (fileToAnalyze == null) {
            return null
        }

        val isInProjectSource = RootKindFilter.projectSources.matches(fileToAnalyze)
                && moduleInfo.contentScope.contains(fileToAnalyze)

        return if (!isInProjectSource) fileToAnalyze else null
    }

    private fun Collection<KtFile>.filterScripts(): Set<KtFile> =
        mapNotNullTo(mutableSetOf()) { it.filterScript() }

    private fun KtFile.filterScript(): KtFile? {
        val contextFile = if (this is KtCodeFragment) this.getContextFile() else this
        return contextFile?.takeIf { it.isScript() }
    }

    private fun KtFile.isScript(): Boolean {
        val contextFile = if (this is KtCodeFragment) this.getContextFile() else this
        return contextFile?.isScript() ?: false
    }

    private fun KtCodeFragment.getContextFile(): KtFile? {
        val contextElement = context ?: return null
        val contextFile = (contextElement as? KtElement)?.containingKtFile
            ?: throw AssertionError("Analyzing kotlin code fragment of type ${this::class.java} with java context of type ${contextElement::class.java}")
        return if (contextFile is KtCodeFragment) contextFile.getContextFile() else contextFile
    }
}

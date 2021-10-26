// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.resolve

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
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.SLRUCache
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.analyzer.ResolverForProject.Companion.resolverForLibrariesName
import org.jetbrains.kotlin.analyzer.ResolverForProject.Companion.resolverForModulesName
import org.jetbrains.kotlin.analyzer.ResolverForProject.Companion.resolverForScriptDependenciesName
import org.jetbrains.kotlin.analyzer.ResolverForProject.Companion.resolverForSdkName
import org.jetbrains.kotlin.analyzer.ResolverForProject.Companion.resolverForSpecialInfoName
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.caches.resolve.PlatformAnalysisSettings
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.context.GlobalContext
import org.jetbrains.kotlin.context.GlobalContextImpl
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.idea.caches.project.*
import org.jetbrains.kotlin.idea.caches.project.IdeaModuleInfo
import org.jetbrains.kotlin.idea.caches.resolve.util.GlobalFacadeModuleFilters
import org.jetbrains.kotlin.idea.caches.resolve.util.contextWithCompositeExceptionTracker
import org.jetbrains.kotlin.idea.caches.trackers.outOfBlockModificationCount
import org.jetbrains.kotlin.idea.compiler.IDELanguageSettingsProvider
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.dependencies.ScriptAdditionalIdeaDependenciesProvider
import org.jetbrains.kotlin.idea.project.TargetPlatformDetector
import org.jetbrains.kotlin.idea.project.useCompositeAnalysis
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.ProjectRootsUtil
import org.jetbrains.kotlin.idea.util.application.withPsiAttachment
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.contains
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.diagnostics.KotlinSuppressCache
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong

internal val LOG = Logger.getInstance(KotlinCacheService::class.java)

data class PlatformAnalysisSettingsImpl(
    val platform: TargetPlatform,
    val sdk: Sdk?,
    val isAdditionalBuiltInFeaturesSupported: Boolean,
) : PlatformAnalysisSettings

object CompositeAnalysisSettings : PlatformAnalysisSettings

fun createPlatformAnalysisSettings(
    project: Project,
    platform: TargetPlatform,
    sdk: Sdk?,
    isAdditionalBuiltInFeaturesSupported: Boolean
) = if (project.useCompositeAnalysis)
    CompositeAnalysisSettings
else
    PlatformAnalysisSettingsImpl(platform, sdk, isAdditionalBuiltInFeaturesSupported)


class KotlinCacheServiceImpl(val project: Project) : KotlinCacheService {
    override fun getResolutionFacade(element: KtElement): ResolutionFacade {
        val file = element.fileForElement()
        if (file.isScript()) {
            // Scripts support seem to modify some of the important aspects via file user data without triggering PsiModificationTracker.
            // If in doubt, run ScriptDefinitionsOrderTestGenerated
            return getFacadeToAnalyzeFile(file, TargetPlatformDetector.getPlatform(file))
        }
        else {
            return CachedValuesManager.getCachedValue(file) {
                CachedValueProvider.Result(
                    getFacadeToAnalyzeFile(file, TargetPlatformDetector.getPlatform(file)),
                    PsiModificationTracker.MODIFICATION_COUNT
                )
            }
        }
    }

    override fun getResolutionFacade(elements: List<KtElement>): ResolutionFacade {
        val files = getFilesForElements(elements)

        if (files.size == 1) return getResolutionFacade(files.single())
        return getFacadeToAnalyzeFiles(files, TargetPlatformDetector.getPlatform(files.first()))
    }

    override fun getResolutionFacade(elements: List<KtElement>, platform: TargetPlatform): ResolutionFacade {
        val files = getFilesForElements(elements)
        return getFacadeToAnalyzeFiles(files, platform)
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
        object : SLRUCache<PlatformAnalysisSettings, GlobalFacade>(2 * 3 * 2, 2 * 3 * 2) {
            override fun createValue(settings: PlatformAnalysisSettings): GlobalFacade {
                return GlobalFacade(settings)
            }
        }

    private val facadeForScriptDependenciesForProject = createFacadeForScriptDependencies(ScriptDependenciesInfo.ForProject(project))

    private fun createFacadeForScriptDependencies(
        dependenciesModuleInfo: ScriptDependenciesInfo
    ): ProjectResolutionFacade {
        val sdk = dependenciesModuleInfo.sdk
        val platform = JvmPlatforms.defaultJvmPlatform // TODO: Js scripts?
        val settings = createPlatformAnalysisSettings(project, platform, sdk, true)

        val dependenciesForScriptDependencies = listOf(
            LibraryModificationTracker.getInstance(project),
            ProjectRootModificationTracker.getInstance(project),
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
            invalidateOnOOCB = true
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
                LibraryModificationTracker.getInstance(project),
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
                LibraryModificationTracker.getInstance(project),
                ProjectRootModificationTracker.getInstance(project)
            )
        )

        private val modulesContext = librariesContext.contextWithCompositeExceptionTracker(project, resolverForModulesName)
        val facadeForModules = ProjectResolutionFacade(
            "facadeForModules", "$resolverForModulesName with settings=$settings",
            project, modulesContext, settings,
            reuseDataFrom = facadeForLibraries,
            moduleFilter = moduleFilters::moduleFacadeFilter,
            dependencies = listOf(
                LibraryModificationTracker.getInstance(project),
                ProjectRootModificationTracker.getInstance(project)
            ),
            invalidateOnOOCB = true
        )
    }

    private fun IdeaModuleInfo.platformSettings(targetPlatform: TargetPlatform) = createPlatformAnalysisSettings(
        this@KotlinCacheServiceImpl.project, targetPlatform, sdk,
        supportsAdditionalBuiltInsMembers(this@KotlinCacheServiceImpl.project)
    )

    private fun facadeForModules(settings: PlatformAnalysisSettings) =
        getOrBuildGlobalFacade(settings).facadeForModules

    private fun librariesFacade(settings: PlatformAnalysisSettings) =
        getOrBuildGlobalFacade(settings).facadeForLibraries

    @Synchronized
    private fun getOrBuildGlobalFacade(settings: PlatformAnalysisSettings) =
        globalFacadesPerPlatformAndSdk[settings]

    private fun createFacadeForFilesWithSpecialModuleInfo(files: Set<KtFile>): ProjectResolutionFacade {
        // we assume that all files come from the same module
        val targetPlatform = files.map { TargetPlatformDetector.getPlatform(it) }.toSet().single()
        val specialModuleInfo = files.map(KtFile::getModuleInfo).toSet().single()
        val settings = specialModuleInfo.platformSettings(specialModuleInfo.platform ?: targetPlatform)

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
                dependencies = listOf(dependencyTrackerForSyntheticFileCache),
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
            specialModuleInfo is ScriptDependenciesInfo -> facadeForScriptDependenciesForProject
            specialModuleInfo is ScriptDependenciesSourceInfo -> {
                val globalContext =
                    facadeForScriptDependenciesForProject.globalContext.contextWithCompositeExceptionTracker(
                        project,
                        "facadeForSpecialModuleInfo (ScriptDependenciesSourceInfo)"
                    )
                makeProjectResolutionFacade(
                    "facadeForSpecialModuleInfo (ScriptDependenciesSourceInfo)",
                    globalContext,
                    reuseDataFrom = facadeForScriptDependenciesForProject,
                    allModules = specialModuleInfo.dependencies(),
                    moduleFilter = { it == specialModuleInfo }
                )
            }

            specialModuleInfo is LibrarySourceInfo || specialModuleInfo === NotUnderContentRootModuleInfo -> {
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
            CachedValueProvider.Result<KotlinSuppressCache>(
                object : KotlinSuppressCache() {
                    override fun getSuppressionAnnotations(annotated: PsiElement): List<AnnotationDescriptor> {
                        if (annotated !is KtAnnotated) return emptyList()
                        if (annotated.annotationEntries.none {
                                it.calleeExpression?.text?.endsWith(SUPPRESS_ANNOTATION_SHORT_NAME) == true
                            }
                        ) {
                            // Avoid running resolve heuristics
                            // TODO: Check aliases in imports
                            return emptyList()
                        }

                        val context =
                            when (annotated) {
                                is KtFile -> {
                                    annotated.fileAnnotationList?.analyze(BodyResolveMode.PARTIAL)
                                        ?: return emptyList()
                                }
                                is KtModifierListOwner -> {
                                    annotated.modifierList?.analyze(BodyResolveMode.PARTIAL)
                                        ?: return emptyList()
                                }
                                else ->
                                    annotated.analyze(BodyResolveMode.PARTIAL)
                            }

                        val annotatedDescriptor = context.get(BindingContext.DECLARATION_TO_DESCRIPTOR, annotated)

                        if (annotatedDescriptor != null) {
                            return annotatedDescriptor.annotations.toList()
                        }

                        return annotated.annotationEntries.mapNotNull {
                            context.get(
                                BindingContext.ANNOTATION,
                                it
                            )
                        }
                    }
                },
                LibraryModificationTracker.getInstance(project),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        },
        false
    )

    private val specialFilesCacheProvider = CachedValueProvider {
        // NOTE: computations inside createFacadeForFilesWithSpecialModuleInfo depend on project root structure
        // so we additionally drop the whole slru cache on change
        CachedValueProvider.Result(
            object : SLRUCache<Set<KtFile>, ProjectResolutionFacade>(2, 3) {
                override fun createValue(files: Set<KtFile>) = createFacadeForFilesWithSpecialModuleInfo(files)
            },
            LibraryModificationTracker.getInstance(project),
            ProjectRootModificationTracker.getInstance(project)
        )
    }

    private fun getFacadeForSpecialFiles(files: Set<KtFile>): ProjectResolutionFacade {
        val cachedValue: SLRUCache<Set<KtFile>, ProjectResolutionFacade> =
            CachedValuesManager.getManager(project).getCachedValue(project, specialFilesCacheProvider)

        // In Upsource, we create multiple instances of KotlinCacheService, which all access the same CachedValue instance (UP-8046)
        // This is so because class name of provider is used as a key when fetching cached value, see CachedValueManager.getKeyForClass.
        // To avoid race conditions, we can't use any local lock to access the cached value contents.
        return cachedValue.getOrCreateValue(files)
    }

    private val scriptsCacheProvider = CachedValueProvider {
        CachedValueProvider.Result(
            object : SLRUCache<Set<KtFile>, ProjectResolutionFacade>(10, 5) {
                override fun createValue(files: Set<KtFile>) = createFacadeForFilesWithSpecialModuleInfo(files)
            },
            LibraryModificationTracker.getInstance(project),
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
            val newValue = this.createValue(key)!!
            synchronized(this) {
                val cached = this.getIfCached(key)
                cached ?: run {
                    this.put(key, newValue)
                    newValue
                }
            }
        }

    private fun getFacadeToAnalyzeFiles(files: Collection<KtFile>, platform: TargetPlatform): ResolutionFacade {
        val file = files.first()
        val moduleInfo = file.getModuleInfo()
        val specialFiles = files.filterNotInProjectSource(moduleInfo)
        val scripts = specialFiles.filterScripts()
        if (scripts.isNotEmpty()) {
            val projectFacade = getFacadeForScripts(scripts)
            return ModuleResolutionFacadeImpl(projectFacade, moduleInfo).createdFor(scripts, moduleInfo)
        }

        if (specialFiles.isNotEmpty()) {
            val projectFacade = getFacadeForSpecialFiles(specialFiles)
            return ModuleResolutionFacadeImpl(projectFacade, moduleInfo).createdFor(specialFiles, moduleInfo)
        }

        return getResolutionFacadeByModuleInfo(moduleInfo, platform).createdFor(emptyList(), moduleInfo, platform)
    }

    private fun getFacadeToAnalyzeFile(file: KtFile, platform: TargetPlatform): ResolutionFacade {
        val moduleInfo = file.getModuleInfo()
        val specialFile = file.filterNotInProjectSource(moduleInfo)

        specialFile?.filterScript()?.let { script ->
            val scripts = setOf(script)
            val projectFacade = getFacadeForScripts(scripts)
            return ModuleResolutionFacadeImpl(projectFacade, moduleInfo).createdFor(scripts, moduleInfo)
        }

        if (specialFile != null) {
            val specialFiles = setOf(specialFile)
            val projectFacade = getFacadeForSpecialFiles(specialFiles)
            return ModuleResolutionFacadeImpl(projectFacade, moduleInfo).createdFor(specialFiles, moduleInfo)
        }

        return getResolutionFacadeByModuleInfo(moduleInfo, platform).createdFor(emptyList(), moduleInfo, platform)
    }

    override fun getResolutionFacadeByFile(file: PsiFile, platform: TargetPlatform): ResolutionFacade? {
        if (!ProjectRootsUtil.isInProjectOrLibraryContent(file)) {
            return null
        }

        assert(file !is PsiCodeFragment)

        val moduleInfo = file.getModuleInfo()
        return getResolutionFacadeByModuleInfo(moduleInfo, platform)
    }

    private fun getResolutionFacadeByModuleInfo(moduleInfo: IdeaModuleInfo, platform: TargetPlatform): ResolutionFacade {
        val settings = moduleInfo.platformSettings(platform)
        return getResolutionFacadeByModuleInfoAndSettings(moduleInfo, settings)
    }

    private fun getResolutionFacadeByModuleInfoAndSettings(
        moduleInfo: IdeaModuleInfo,
        settings: PlatformAnalysisSettings
    ): ResolutionFacade {
        val projectFacade = when (moduleInfo) {
            is ScriptDependenciesInfo.ForProject,
            is ScriptDependenciesSourceInfo.ForProject -> facadeForScriptDependenciesForProject
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
        mapNotNullTo(mutableSetOf()) { it.filterNotInProjectSource(moduleInfo) }

    private fun KtFile.filterNotInProjectSource(moduleInfo: IdeaModuleInfo): KtFile? {
        val contextFile = if (this is KtCodeFragment) this.getContextFile() else this
        return contextFile?.takeIf {
            !ProjectRootsUtil.isInProjectSource(it) || !moduleInfo.contentScope().contains(it)
        }
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

    private companion object {
        private val SUPPRESS_ANNOTATION_SHORT_NAME = StandardNames.FqNames.suppress.shortName().identifier
    }
}

fun IdeaModuleInfo.supportsAdditionalBuiltInsMembers(project: Project): Boolean {
    return IDELanguageSettingsProvider
        .getLanguageVersionSettings(this, project)
        .supportsFeature(LanguageFeature.AdditionalBuiltInsMembers)
}

val IdeaModuleInfo.sdk: Sdk? get() = dependencies().firstIsInstanceOrNull<SdkInfo>()?.sdk

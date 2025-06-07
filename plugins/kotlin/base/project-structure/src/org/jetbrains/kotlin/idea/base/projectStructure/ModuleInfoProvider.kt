// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.projectStructure.analysisContextModule
import org.jetbrains.kotlin.analysis.decompiled.light.classes.KtLightClassForDecompiledDeclaration
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.config.KotlinSourceRootType
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibrarySourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.idea.base.util.*
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.analysisContext
import org.jetbrains.kotlin.psi.doNotAnalyze
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

@DslMarker
private annotation class ModuleInfoDsl

@ModuleInfoDsl
@K1ModeProjectStructureApi
suspend fun SequenceScope<Result<IdeaModuleInfo>>.register(moduleInfo: IdeaModuleInfo): Unit = yield(Result.success(moduleInfo))

@ModuleInfoDsl
@K1ModeProjectStructureApi
suspend fun SequenceScope<Result<IdeaModuleInfo>>.register(block: () -> IdeaModuleInfo?) {
    block()?.let { register(it) }
}

@ModuleInfoDsl
@K1ModeProjectStructureApi
suspend fun SequenceScope<Result<IdeaModuleInfo>>.reportError(error: Throwable): Unit = yield(Result.failure(error))

@K1ModeProjectStructureApi
interface ModuleInfoProviderExtension {
    companion object {
        val EP_NAME: ExtensionPointName<ModuleInfoProviderExtension> =
            ExtensionPointName("org.jetbrains.kotlin.idea.base.projectStructure.moduleInfoProviderExtension")
    }

    suspend fun SequenceScope<Result<IdeaModuleInfo>>.collectByElement(element: PsiElement, file: PsiFile, virtualFile: VirtualFile)
    suspend fun SequenceScope<Result<IdeaModuleInfo>>.collectByFile(
        project: Project,
        virtualFile: VirtualFile,
        isLibrarySource: Boolean,
        config: ModuleInfoProvider.Configuration,
    )

    suspend fun SequenceScope<Module>.findContainingModules(project: Project, virtualFile: VirtualFile)
}

@Service(Service.Level.PROJECT)
@K1ModeProjectStructureApi
class ModuleInfoProvider(private val project: Project) {
    companion object {
        internal val LOG = Logger.getInstance(ModuleInfoProvider::class.java)

        fun getInstance(project: Project): ModuleInfoProvider = project.service()

        fun findAnchorElement(element: PsiElement): PsiElement? = when {
            element is PsiDirectory -> element
            element !is KtLightElement<*, *> -> element.containingFile
            /**
             * We shouldn't unwrap decompiled classes
             * @see [ModuleInfoProvider.collectByLightElement]
             */
            element.getNonStrictParentOfType<KtLightClassForDecompiledDeclaration>() != null -> null
            element is KtLightClassForFacade -> element.files.first()
            else -> element.kotlinOrigin?.let(::findAnchorElement)
        }
    }

    data class Configuration(
        val createSourceLibraryInfoForLibraryBinaries: Boolean = true,
        val preferModulesFromExtensions: Boolean = false,
        val contextualModuleInfo: IdeaModuleInfo? = null,
    ) {
        companion object {
            val Default = Configuration()
        }
    }

    private val fileIndex by lazy { ProjectFileIndex.getInstance(project) }
    private val libraryInfoCache by lazy { LibraryInfoCache.getInstance(project) }

    fun collect(element: PsiElement, config: Configuration = Configuration.Default): Sequence<Result<IdeaModuleInfo>> {
        return sequence {
            collectByElement(element, config)
        }
    }

    fun collect(
        virtualFile: VirtualFile,
        isLibrarySource: Boolean = false,
        config: Configuration = Configuration.Default,
    ): Sequence<Result<IdeaModuleInfo>> {
        return sequence {
            collectByFile(virtualFile, isLibrarySource, config)
        }
    }

    private suspend fun SequenceScope<Result<IdeaModuleInfo>>.collectByElement(element: PsiElement, config: Configuration) {
        PsiUtilCore.ensureValid(element)
        val containingFile = element.containingFile

        if (containingFile != null) {
            val moduleInfo = containingFile.forcedModuleInfo
            if (moduleInfo is IdeaModuleInfo) {
                register(moduleInfo)
            }
        }

        if (element is PsiDirectory) {
            collectByFile(element.virtualFile, isLibrarySource = false, config)
            return
        }

        if (element is KtLightElement<*, *>) {
            collectByLightElement(element, config)
        }

        collectByUserData(UserDataModuleContainer.ForElement(element))

        if (containingFile == null) {
            val message = "Analyzing element of type ${element::class.java} with no containing file"
            reportError(KotlinExceptionWithAttachments(message).withAttachment("element.kt", element.text))
        }

        val containingKtFile = containingFile as? KtFile
        if (containingKtFile != null) {
            @OptIn(KaImplementationDetail::class, K1ModeProjectStructureApi::class)
            containingFile.virtualFile?.analysisContextModule?.let { module ->
                register(module.moduleInfo)
            }

            val analysisContext = containingKtFile.analysisContext
            if (analysisContext != null) {
                collectByElement(analysisContext, config)
            }

            if (containingKtFile.doNotAnalyze != null) {
                return
            }

            val explicitModuleInfo = containingKtFile.forcedModuleInfo ?: (containingKtFile.originalFile as? KtFile)?.forcedModuleInfo
            if (explicitModuleInfo is IdeaModuleInfo) {
                register(explicitModuleInfo)
            }

            if (containingKtFile is KtCodeFragment) {
                val context = containingKtFile.getContext()
                if (context != null) {
                    collectByElement(context, config)
                } else {
                    val message = "Analyzing code fragment of type ${containingKtFile::class.java} with no context"
                    val error = KotlinExceptionWithAttachments(message).withAttachment("file.kt", containingKtFile.text)
                    reportError(error)
                }
            }
        }

        if (containingFile != null) {
            val virtualFile = containingFile.originalFile.virtualFile
            if (virtualFile != null) {
                withCallExtensions(
                    config = config,
                    extensionBlock = { collectByElement(element, containingFile, virtualFile) },
                ) {
                    val isLibrarySource = containingKtFile != null && isLibrarySource(containingKtFile, config)
                    collectByFile(virtualFile, isLibrarySource, config)
                }
            } else {
                val message = "Analyzing element of type ${element::class.java} in non-physical file of type ${containingFile::class.java}"
                reportError(KotlinExceptionWithAttachments(message).withAttachment("file.kt", containingFile.text))
            }
        }
    }

    private inline fun callExtensions(block: ModuleInfoProviderExtension.() -> Unit) {
        for (extension in project.extensionArea.getExtensionPoint(ModuleInfoProviderExtension.EP_NAME).extensionList) {
            with(extension, block)
        }
    }

    private inline fun withCallExtensions(
        config: Configuration,
        extensionBlock: ModuleInfoProviderExtension.() -> Unit,
        block: () -> Unit,
    ) {
        if (config.preferModulesFromExtensions) {
            callExtensions(extensionBlock)
        }

        block()

        if (!config.preferModulesFromExtensions) {
            callExtensions(extensionBlock)
        }
    }

    private fun isLibrarySource(containingKtFile: KtFile, config: Configuration): Boolean {
        val isCompiled = containingKtFile.isCompiled
        return if (config.createSourceLibraryInfoForLibraryBinaries) isCompiled else !isCompiled
    }

    private suspend fun SequenceScope<Result<IdeaModuleInfo>>.collectByLightElement(element: KtLightElement<*, *>, config: Configuration) {
        if (element.getNonStrictParentOfType<KtLightClassForDecompiledDeclaration>() != null) {
            val virtualFile = element.containingFile.virtualFile ?: error("Decompiled class should be build from physical file")
            collectByFile(virtualFile, isLibrarySource = false, config)
        }

        val originalElement = element.kotlinOrigin
        if (originalElement != null) {
            collectByElement(originalElement, config)
        } else if (element is KtLightClassForFacade) {
            collectByElement(element.files.first(), config)
        } else {
            val error = KotlinExceptionWithAttachments("Light element without origin is referenced by resolve")
                .withAttachment("element.txt", element)

            reportError(error)
        }
    }

    private suspend fun SequenceScope<Result<IdeaModuleInfo>>.collectByFile(
        virtualFile: VirtualFile,
        isLibrarySource: Boolean,
        config: Configuration,
    ) {
        collectByUserData(UserDataModuleContainer.ForVirtualFile(virtualFile, project))
        withCallExtensions(
            config = config,
            extensionBlock = { collectByFile(project, virtualFile, isLibrarySource, config) }
        ) {
            collectSourceRelatedByFile(virtualFile, config)

            val visited = hashSetOf<IdeaModuleInfo>()
            val collectionRequest = VirtualFileCollectionRequest(virtualFile, isLibrarySource, config, visited, project)

            // Several libraries may include the same JAR files.
            // Below, we use an index for getting order entries for a file, but entries come in an arbitrary order.
            // So if we are already inside a library, we scan it first.

            val contextualModuleResult = contextByContextualBinaryModule(collectionRequest)
            contextualModuleResult?.let { yield(Result.success(it)) }

            val orderEntries = runReadAction { fileIndex.getOrderEntriesForFile(virtualFile) }
            yieldAll(
                orderEntries.asSequence().mapNotNull { orderEntry ->
                    collectByOrderEntry(collectionRequest, orderEntry)?.let(Result.Companion::success)
                }
            )
        }
    }

    private class VirtualFileCollectionRequest(
        val virtualFile: VirtualFile,
        val isLibrarySource: Boolean,
        val config: Configuration,
        val visited: HashSet<IdeaModuleInfo>,
        val project: Project,
    ) {
        val hasLibraryClassesRootKind: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
            RootKindFilter.libraryClasses.matches(project, virtualFile)
        }

        val hasLibraryFilesRootKind: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
            RootKindFilter.libraryFiles.matches(project, virtualFile)
        }
    }

    private fun contextByContextualBinaryModule(collectionRequest: VirtualFileCollectionRequest): IdeaModuleInfo? {
        val contextualModuleInfo = collectionRequest.config.contextualModuleInfo ?: return null

        val contentScope = when (contextualModuleInfo) {
            is LibraryInfo, is SdkInfo -> contextualModuleInfo.contentScope
            is LibrarySourceInfo -> contextualModuleInfo.sourceScope()
            else -> null
        }

        val virtualFile = collectionRequest.virtualFile
        if (contentScope == null || virtualFile !in contentScope) {
            return null
        }

        return when (contextualModuleInfo) {
            is LibraryInfo -> collectByLibrary(collectionRequest, contextualModuleInfo.library)
            is LibrarySourceInfo -> collectByLibrary(collectionRequest, contextualModuleInfo.library)
            is SdkInfo -> collectBySdk(collectionRequest, contextualModuleInfo.sdk)
            else -> null
        }
    }

    private suspend fun SequenceScope<Result<IdeaModuleInfo>>.collectSourceRelatedByFile(virtualFile: VirtualFile, config: Configuration) {
        val modules = sequence {
            withCallExtensions(
                config = config,
                extensionBlock = { findContainingModules(project, virtualFile) },
            ) {
                runReadAction { fileIndex.getModuleForFile(virtualFile) }?.let { module ->
                    yield(module)
                }
            }
        }
        yieldAll(
            modules.mapNotNull { module ->
                if (module.isDisposed) return@mapNotNull null
                val projectFileIndex = ProjectFileIndex.getInstance(project)
                val sourceRootType: KotlinSourceRootType? = projectFileIndex.getKotlinSourceRootType(virtualFile)
                module.asSourceInfo(sourceRootType)?.let(Result.Companion::success)
            }
        )

        val fileOrigin = getOutsiderFileOrigin(project, virtualFile)
        if (fileOrigin != null) {
            collectSourceRelatedByFile(fileOrigin, config)
        }
    }

    private fun collectByOrderEntry(
        collectionRequest: VirtualFileCollectionRequest,
        orderEntry: OrderEntry,
    ): IdeaModuleInfo? {
        if (orderEntry is ModuleOrderEntry) {
            // Module-related entries are covered in 'collectModuleRelatedModuleInfosByFile()'
            return null
        }

        ProgressManager.checkCanceled()

        if (!orderEntry.isValid) {
            return null
        }

        if (orderEntry is LibraryOrderEntry) {
            val library = orderEntry.library
            if (library != null) {
                return collectByLibrary(collectionRequest, library)
            }
        }

        if (orderEntry is JdkOrderEntry) {
            val sdk = orderEntry.jdk
            if (sdk != null) {
                val contextSdk = collectionRequest.config.contextualModuleInfo?.sdk()
                if (contextSdk != null && contextSdk != sdk) {
                    // don't yield sdk which is absent in the dependencies
                    return null
                }
                return collectBySdk(collectionRequest, sdk)
            }
        }

        return null
    }

    private fun collectByLibrary(
        collectionRequest: VirtualFileCollectionRequest,
        library: Library,
    ): IdeaModuleInfo? {
        val config = collectionRequest.config
        val sourceContext = config.contextualModuleInfo as? ModuleSourceInfo
        val useLibrarySource = collectionRequest.isLibrarySource || (config.contextualModuleInfo as? LibrarySourceInfo)?.library == library

        if (!useLibrarySource && collectionRequest.hasLibraryClassesRootKind) {
            for (libraryInfo in libraryInfoCache[library]) {
                val isNew = collectionRequest.visited.add(libraryInfo)
                if (isNew && libraryInfo.isApplicable(sourceContext)) {
                    return libraryInfo
                }
            }
        } else if (useLibrarySource || collectionRequest.hasLibraryFilesRootKind) {
            for (libraryInfo in libraryInfoCache[library]) {
                val sourceInfo = libraryInfo.sourcesModuleInfo
                val isNew = collectionRequest.visited.add(sourceInfo)
                if (isNew && libraryInfo.isApplicable(sourceContext)) {
                    return sourceInfo
                }
            }
        }

        return null
    }

    private fun collectBySdk(collectionRequest: VirtualFileCollectionRequest, sdk: Sdk): IdeaModuleInfo? {
        val moduleInfo = SdkInfo(project, sdk)
        if (collectionRequest.visited.add(moduleInfo)) {
            return moduleInfo
        }

        return null
    }

    private fun LibraryInfo.isApplicable(contextualModuleInfo: ModuleSourceInfo?): Boolean {
        if (contextualModuleInfo == null) return true
        return contextualModuleInfo.module.hasLibraryInTransitiveDependencies(library)
    }

    private fun Module.hasLibraryInTransitiveDependencies(library: Library): Boolean {
        var result = false
        ModuleRootManager.getInstance(this).orderEntries()
            .librariesOnly()
            .recursively().exportedOnly()
            .forEachLibrary {
                if (it == library) {
                    result = true
                    return@forEachLibrary false
                }
                true
            }
        return result
    }

    private suspend fun SequenceScope<Result<IdeaModuleInfo>>.collectByUserData(container: UserDataModuleContainer) {
        register {
            val sourceRootType = container.customSourceRootType
            if (sourceRootType == null) return@register null

            // Compute `module` last. `ModuleUtilCore.findModuleForPsiElement` is costly to compute as it iterates through all libraries of
            // a module to find the virtual file possibly in a module-only library. At the same time, `customSourceRootType` only applies to
            // Android light classes, which are an edge case, so we can assume that it'll be `null` most of the time.
            container.module?.asSourceInfo(sourceRootType.sourceRootType)?.let { moduleInfo ->
                return@register moduleInfo
            }
            null
        }

        container.customLibrary?.let { library ->
            for (libraryInfo in libraryInfoCache[library]) {
                register(libraryInfo)
            }
        }

        register {
            container.customSdk?.let { SdkInfo(project, it) }
        }
    }
}

@K1ModeProjectStructureApi
private sealed class UserDataModuleContainer {
    abstract val module: Module?

    val customSourceRootType: JpsModuleSourceRootType<*>?
        get() = holders.firstNotNullOfOrNull { it.customSourceRootType }

    val customSdk: Sdk?
        get() = holders.firstNotNullOfOrNull { it.customSdk }

    val customLibrary: Library?
        get() = holders.firstNotNullOfOrNull { it.customLibrary }

    abstract val holders: List<UserDataHolder>

    data class ForVirtualFile(val virtualFile: VirtualFile, val project: Project) : UserDataModuleContainer() {
        override val module: Module?
            get() = ModuleUtilCore.findModuleForFile(virtualFile, project)

        override val holders: List<UserDataHolder>
            get() = listOf(virtualFile)
    }

    data class ForElement(val psiElement: PsiElement) : UserDataModuleContainer() {
        override val module: Module?
            get() = ModuleUtilCore.findModuleForPsiElement(psiElement)

        override val holders: List<UserDataHolder> by lazy {
            val containingFile = psiElement.containingFile
            listOfNotNull(
                psiElement,
                containingFile,
                containingFile?.originalFile?.virtualFile
            )
        }
    }
}

@ApiStatus.Internal
@K1ModeProjectStructureApi
fun Sequence<Result<IdeaModuleInfo>>.unwrap(
    errorHandler: (String, Throwable) -> Unit,
): Sequence<IdeaModuleInfo> = sequence {
    for (result in this@unwrap) {
        val successResult = result.getOrNull()
        if (successResult != null) {
            yield(successResult)
            continue
        }
        val error = result.exceptionOrNull()
        if (error != null) {
            errorHandler("Could not find correct module information", error)
            break
        }
    }
}
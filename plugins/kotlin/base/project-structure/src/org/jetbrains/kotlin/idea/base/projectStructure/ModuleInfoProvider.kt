// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.analysis.decompiled.light.classes.KtLightClassForDecompiledDeclaration
import org.jetbrains.kotlin.analysis.project.structure.KtModuleStructureInternals
import org.jetbrains.kotlin.analysis.project.structure.analysisExtensionFileContextModule
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.classes.runReadAction
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.config.KotlinSourceRootType
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
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
fun SeqScope<Result<IdeaModuleInfo>>.register(moduleInfo: IdeaModuleInfo) = yield { Result.success(moduleInfo) }

@ModuleInfoDsl
fun SeqScope<Result<IdeaModuleInfo>>.register(block: () -> IdeaModuleInfo?) = yield {
    block()?.let(Result.Companion::success)
}

@ModuleInfoDsl
fun SeqScope<Result<IdeaModuleInfo>>.reportError(error: Throwable) = yield { Result.failure(error) }

interface ModuleInfoProviderExtension {
    companion object {
        val EP_NAME: ExtensionPointName<ModuleInfoProviderExtension> =
            ExtensionPointName("org.jetbrains.kotlin.idea.base.projectStructure.moduleInfoProviderExtension")
    }

    fun SeqScope<Result<IdeaModuleInfo>>.collectByElement(element: PsiElement, file: PsiFile, virtualFile: VirtualFile)
    fun SeqScope<Result<IdeaModuleInfo>>.collectByFile(
        project: Project,
        virtualFile: VirtualFile,
        isLibrarySource: Boolean,
        config: ModuleInfoProvider.Configuration,
    )

    fun SeqScope<Module>.findContainingModules(project: Project, virtualFile: VirtualFile)
}

@Service(Service.Level.PROJECT)
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
        return seq {
            collectByElement(element, config)
        }
    }

    fun collect(
        virtualFile: VirtualFile,
        isLibrarySource: Boolean = false,
        config: Configuration = Configuration.Default,
    ): Sequence<Result<IdeaModuleInfo>> {
        return seq {
            collectByFile(virtualFile, isLibrarySource, config)
        }
    }

    private fun SeqScope<Result<IdeaModuleInfo>>.collectByElement(element: PsiElement, config: Configuration) {
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
            @OptIn(KtModuleStructureInternals::class, Frontend10ApiUsage::class)
            containingFile.virtualFile?.analysisExtensionFileContextModule?.let { module ->
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
                    val isLibrarySource = if (containingKtFile != null) isLibrarySource(containingKtFile, config) else false
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

    private fun SeqScope<Result<IdeaModuleInfo>>.collectByLightElement(element: KtLightElement<*, *>, config: Configuration) {
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

    private fun SeqScope<Result<IdeaModuleInfo>>.collectByFile(
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

            yieldAll(object : Iterable<Result<IdeaModuleInfo>> {
                override fun iterator(): Iterator<Result<IdeaModuleInfo>> {
                    val orderEntries = runReadAction { fileIndex.getOrderEntriesForFile(virtualFile) }
                    val iterator = orderEntries.iterator()
                    val visited = hashSetOf<IdeaModuleInfo>()
                    return MappingIterator(iterator) { orderEntry ->
                        collectByOrderEntry(virtualFile, orderEntry, isLibrarySource, visited, config)?.let(Result.Companion::success)
                    }
                }
            })
        }
    }

    private fun SeqScope<Result<IdeaModuleInfo>>.collectSourceRelatedByFile(virtualFile: VirtualFile, config: Configuration) {
        yieldAll(object : Iterable<Result<IdeaModuleInfo>> {
            override fun iterator(): Iterator<Result<IdeaModuleInfo>> {
                val modules = seq {
                    withCallExtensions(
                        config = config,
                        extensionBlock = { findContainingModules(project, virtualFile) },
                    ) {
                        runReadAction { fileIndex.getModuleForFile(virtualFile) }?.let { module ->
                            yield { module }
                        }
                    }
                }
                val iterator = modules.iterator()

                return MappingIterator(iterator) { module ->
                    if (module.isDisposed) return@MappingIterator null
                    val projectFileIndex = ProjectFileIndex.getInstance(project)
                    val sourceRootType: KotlinSourceRootType? = projectFileIndex.getKotlinSourceRootType(virtualFile)
                    module.asSourceInfo(sourceRootType)?.let(Result.Companion::success)
                }
            }
        })

        val fileOrigin = getOutsiderFileOrigin(project, virtualFile)
        if (fileOrigin != null) {
            collectSourceRelatedByFile(fileOrigin, config)
        }
    }

    private fun collectByOrderEntry(
        virtualFile: VirtualFile,
        orderEntry: OrderEntry,
        isLibrarySource: Boolean,
        visited: HashSet<IdeaModuleInfo>,
        config: Configuration,
    ): IdeaModuleInfo? {
        if (orderEntry is ModuleOrderEntry) {
            // Module-related entries are covered in 'collectModuleRelatedModuleInfosByFile()'
            return null
        }

        val sourceContext = config.contextualModuleInfo as? ModuleSourceInfo

        ProgressManager.checkCanceled()
        if (!orderEntry.isValid) return null

        if (orderEntry is LibraryOrderEntry) {
            val library = orderEntry.library
            if (library != null) {
                if (!isLibrarySource && RootKindFilter.libraryClasses.matches(project, virtualFile)) {
                    for (libraryInfo in libraryInfoCache[library]) {
                        if (visited.add(libraryInfo)) {
                            if (libraryInfo.isApplicable(sourceContext)) {
                                return libraryInfo
                            }
                        }
                    }
                } else if (isLibrarySource || RootKindFilter.libraryFiles.matches(project, virtualFile)) {
                    for (libraryInfo in libraryInfoCache[library]) {
                        val moduleInfo = libraryInfo.sourcesModuleInfo
                        if (visited.add(moduleInfo)) {
                            if (libraryInfo.isApplicable(sourceContext)) {
                                return moduleInfo
                            }
                        }
                    }
                }
                return null
            }
        }

        if (orderEntry is JdkOrderEntry) {
            val sdk = orderEntry.jdk
            if (sdk != null) {
                val moduleInfo = SdkInfo(project, sdk)
                if (visited.add(moduleInfo)) {
                    return moduleInfo
                }
            }
        }
        return null
    }

    private fun LibraryInfo.isApplicable(contextualModuleInfo: ModuleSourceInfo?): Boolean {
        if (contextualModuleInfo == null) return true

        val service = project.service<LibraryUsageIndex>()
        return service.hasDependentModule(this, contextualModuleInfo.module)
    }

    private fun SeqScope<Result<IdeaModuleInfo>>.collectByUserData(container: UserDataModuleContainer) {
        register {
            val module = container.module
            val sourceRootType = container.customSourceRootType

            if (module != null && sourceRootType != null) {
                module.asSourceInfo(sourceRootType.sourceRootType)?.let {moduleInfo ->
                    return@register moduleInfo
                }
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
fun Sequence<Result<IdeaModuleInfo>>.unwrap(
    errorHandler: (String, Throwable) -> Unit,
    stopOnErrors: Boolean = true
): Sequence<IdeaModuleInfo> {
    val originalSequence = this
    return object : Sequence<IdeaModuleInfo> {
        override fun iterator(): Iterator<IdeaModuleInfo> {
            return object : TransformingIterator<IdeaModuleInfo>() {
                private var iterator: Iterator<Result<IdeaModuleInfo>>? = originalSequence.iterator()
                override fun calculateHasNext(): Boolean = iterator?.hasNext() == true

                override fun calculateNext(): IdeaModuleInfo? {
                    val iter = iterator ?: return null
                    val result = iter.next()
                    result.getOrNull()?.let { return it }

                    val error = result.exceptionOrNull()
                    if (error != null) {
                        errorHandler("Could not find correct module information", error)
                        if (stopOnErrors) {
                            iterator = null
                        }
                    }
                    return null
                }
            }
        }
    }
}
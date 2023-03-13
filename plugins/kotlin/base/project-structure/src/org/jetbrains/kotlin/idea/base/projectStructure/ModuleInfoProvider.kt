// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.analysis.decompiled.light.classes.KtLightClassForDecompiledDeclaration
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.classes.runReadAction
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.idea.base.util.getOutsiderFileOrigin
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.analysisContext
import org.jetbrains.kotlin.psi.doNotAnalyze
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.utils.yieldIfNotNull

@DslMarker
private annotation class ModuleInfoDsl

@ModuleInfoDsl
suspend fun SequenceScope<Result<IdeaModuleInfo>>.register(moduleInfo: IdeaModuleInfo) = yield(Result.success(moduleInfo))

@ModuleInfoDsl
suspend fun SequenceScope<Result<IdeaModuleInfo>>.reportError(error: Throwable) = yield(Result.failure(error))

interface ModuleInfoProviderExtension {
    companion object {
        val EP_NAME: ExtensionPointName<ModuleInfoProviderExtension> =
            ExtensionPointName("org.jetbrains.kotlin.idea.base.projectStructure.moduleInfoProviderExtension")
    }

    suspend fun SequenceScope<Result<IdeaModuleInfo>>.collectByElement(element: PsiElement, file: PsiFile, virtualFile: VirtualFile)
    suspend fun SequenceScope<Result<IdeaModuleInfo>>.collectByFile(project: Project, virtualFile: VirtualFile, isLibrarySource: Boolean)

    suspend fun SequenceScope<Module>.findContainingModules(project: Project, virtualFile: VirtualFile)
}

@Service(Service.Level.PROJECT)
class ModuleInfoProvider(private val project: Project) {
    companion object {
        internal val LOG = Logger.getInstance(ModuleInfoProvider::class.java)

        fun getInstance(project: Project): ModuleInfoProvider = project.service()
    }

    class Configuration(val createSourceLibraryInfoForLibraryBinaries: Boolean = true) {
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

    fun collect(virtualFile: VirtualFile, isLibrarySource: Boolean = false): Sequence<Result<IdeaModuleInfo>> {
        return sequence {
            collectByFile(virtualFile, isLibrarySource)
        }
    }

    private suspend fun SequenceScope<Result<IdeaModuleInfo>>.collectByElement(element: PsiElement, config: Configuration) {
        val containingFile = element.containingFile

        if (containingFile != null) {
            val moduleInfo = containingFile.forcedModuleInfo
            if (moduleInfo is IdeaModuleInfo) {
                register(moduleInfo)
            }
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

        val virtualFile = containingFile.originalFile.virtualFile
        if (virtualFile != null) {
            val isLibrarySource = if (containingKtFile != null) isLibrarySource(containingKtFile, config) else false
            collectByFile(virtualFile, isLibrarySource)
            callExtensions { collectByElement(element, containingFile, virtualFile) }
        } else {
            val message = "Analyzing element of type ${element::class.java} in non-physical file of type ${containingFile::class.java}"
            reportError(KotlinExceptionWithAttachments(message).withAttachment("file.kt", containingFile.text))
        }
    }

    private inline fun callExtensions(block: ModuleInfoProviderExtension.() -> Unit) {
        for (extension in project.extensionArea.getExtensionPoint(ModuleInfoProviderExtension.EP_NAME).extensions) {
            with(extension) {
                block()
            }
        }
    }

    private fun isLibrarySource(containingKtFile: KtFile, config: Configuration): Boolean {
        val isCompiled = containingKtFile.isCompiled
        return if (config.createSourceLibraryInfoForLibraryBinaries) isCompiled else !isCompiled
    }

    private suspend fun SequenceScope<Result<IdeaModuleInfo>>.collectByLightElement(element: KtLightElement<*, *>, config: Configuration) {
        if (element.getNonStrictParentOfType<KtLightClassForDecompiledDeclaration>() != null) {
            val virtualFile = element.containingFile.virtualFile ?: error("Decompiled class should be build from physical file")
            collectByFile(virtualFile, isLibrarySource = false)
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

    private suspend fun SequenceScope<Result<IdeaModuleInfo>>.collectByFile(virtualFile: VirtualFile, isLibrarySource: Boolean) {
        collectByUserData(UserDataModuleContainer.ForVirtualFile(virtualFile, project))
        collectSourceRelatedByFile(virtualFile)

        val orderEntries = runReadAction { fileIndex.getOrderEntriesForFile(virtualFile) }
        val visited = hashSetOf<IdeaModuleInfo>()
        for (orderEntry in orderEntries) {
            ProgressManager.checkCanceled()
            collectByOrderEntry(virtualFile, orderEntry, isLibrarySource, visited)
        }

        callExtensions { collectByFile(project, virtualFile, isLibrarySource) }
    }

    private suspend fun SequenceScope<Result<IdeaModuleInfo>>.collectSourceRelatedByFile(virtualFile: VirtualFile) {
        val modules = sequence {
            val module = runReadAction { fileIndex.getModuleForFile(virtualFile) }
            yieldIfNotNull(module)

            callExtensions { findContainingModules(project, virtualFile) }
        }

        for (module in modules) {
            if (module.isDisposed) {
                continue
            }

            val moduleFileIndex = ModuleRootManager.getInstance(module).fileIndex
            when (moduleFileIndex.getKotlinSourceRootType(virtualFile)) {
                null -> {}
                SourceKotlinRootType -> {
                    val moduleInfo = module.productionSourceInfo
                    if (moduleInfo != null) {
                        register(moduleInfo)
                    }
                }
                TestSourceKotlinRootType -> {
                    val moduleInfo = module.testSourceInfo
                    if (moduleInfo != null) {
                        register(moduleInfo)
                    }
                }
            }
        }

        val fileOrigin = getOutsiderFileOrigin(project, virtualFile)
        if (fileOrigin != null) {
            collectSourceRelatedByFile(fileOrigin)
        }
    }

    private suspend fun SequenceScope<Result<IdeaModuleInfo>>.collectByOrderEntry(
      virtualFile: VirtualFile,
      orderEntry: OrderEntry,
      isLibrarySource: Boolean,
      visited: HashSet<IdeaModuleInfo>
    ) {
        if (!orderEntry.isValid || orderEntry is ModuleOrderEntry) {
            // Module-related entries are covered in 'collectModuleRelatedModuleInfosByFile()'
            return
        }

        if (orderEntry is LibraryOrderEntry) {
            val library = orderEntry.library
            if (library != null) {
                if (!isLibrarySource && RootKindFilter.libraryClasses.matches(project, virtualFile)) {
                    for (libraryInfo in libraryInfoCache[library]) {
                        if (visited.add(libraryInfo)) {
                            register(libraryInfo)
                        }
                    }
                } else if (isLibrarySource || RootKindFilter.libraryFiles.matches(project, virtualFile)) {
                    for (libraryInfo in libraryInfoCache[library]) {
                        val moduleInfo = libraryInfo.sourcesModuleInfo
                        if (visited.add(moduleInfo)) {
                            register(moduleInfo)
                        }
                    }
                }
            }
        }

        if (orderEntry is JdkOrderEntry) {
            val sdk = orderEntry.jdk
            if (sdk != null) {
                val moduleInfo = SdkInfo(project, sdk)
                if (visited.add(moduleInfo)) {
                    register(moduleInfo)
                }
            }
        }
    }

    private suspend fun SequenceScope<Result<IdeaModuleInfo>>.collectByUserData(container: UserDataModuleContainer) {
        val module = container.module
        val sourceRootType = container.customSourceRootType

        if (module != null && sourceRootType != null) {
            val moduleInfo = when (sourceRootType.sourceRootType) {
                SourceKotlinRootType -> module.productionSourceInfo
                TestSourceKotlinRootType -> module.testSourceInfo
                else -> null
            }

            if (moduleInfo != null) {
                register(moduleInfo)
            }
        }

        val library = container.customLibrary
        if (library != null) {
            for (libraryInfo in libraryInfoCache[library]) {
                register(libraryInfo)
            }
        }

        val sdk = container.customSdk
        if (sdk != null) {
            register(SdkInfo(project, sdk))
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
            listOfNotNull(
                psiElement,
                psiElement.containingFile,
                psiElement.containingFile?.originalFile?.virtualFile
            )
        }
    }
}

@ApiStatus.Internal
fun Sequence<Result<IdeaModuleInfo>>.unwrap(
    errorHandler: (String, Throwable) -> Unit,
    stopOnErrors: Boolean = true
): Sequence<IdeaModuleInfo> {
    return sequence {
        for (result in this@unwrap) {
            val moduleInfo = result.getOrNull()
            if (moduleInfo != null) {
                yield(moduleInfo)
            }

            val error = result.exceptionOrNull()
            if (error != null) {
                errorHandler("Could not find correct module information", error)
                if (stopOnErrors) {
                    break
                }
            }
        }
    }
}
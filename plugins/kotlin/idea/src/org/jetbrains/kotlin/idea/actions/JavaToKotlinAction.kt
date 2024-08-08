// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.actions

import com.intellij.codeInsight.navigation.activateFileWithPsiElement
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ActionPlaces.PROJECT_VIEW_POPUP
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ex.MessagesEx
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.codeInsight.pathBeforeJavaToKotlinConversion
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider.Companion.isK2Mode
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument
import org.jetbrains.kotlin.idea.configuration.ExperimentalFeatures.NewJ2k
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.statistics.ConversionType
import org.jetbrains.kotlin.idea.statistics.J2KFusCollector
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.getAllFilesRecursively
import org.jetbrains.kotlin.j2k.*
import org.jetbrains.kotlin.j2k.ConverterSettings.Companion.defaultSettings
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.*
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import java.io.IOException
import kotlin.io.path.notExists
import kotlin.system.measureTimeMillis

class JavaToKotlinAction : AnAction() {
    object Handler {
        val title: String = KotlinBundle.message("action.j2k.name")

        @OptIn(KaAllowAnalysisOnEdt::class)
        fun convertFiles(
            files: List<PsiJavaFile>,
            project: Project,
            module: Module,
            enableExternalCodeProcessing: Boolean = true,
            askExternalCodeProcessing: Boolean = true,
            forceUsingOldJ2k: Boolean = false,
            settings: ConverterSettings = defaultSettings
        ): List<KtFile> {
            val javaFiles = files.filter { it.virtualFile.isWritable }.ifEmpty { return emptyList() }
            var converterResult: FilesResult? = null

            fun convertWithStatistics() {
                val j2kKind = getJ2kKind(forceUsingOldJ2k)
                val converter = J2kConverterExtension.extension(j2kKind).createJavaToKotlinConverter(project, module, settings)
                val postProcessor = J2kConverterExtension.extension(j2kKind).createPostProcessor()
                val progressIndicator = ProgressManager.getInstance().progressIndicator!!

                val conversionTime = measureTimeMillis {
                    converterResult = converter.filesToKotlin(
                        javaFiles,
                        postProcessor,
                        progressIndicator,
                        preprocessorExtensions = J2kPreprocessorExtension.EP_NAME.extensionList,
                        postprocessorExtensions = J2kPostprocessorExtension.EP_NAME.extensionList
                    )
                }
                val linesCount = runReadAction {
                    javaFiles.sumOf { StringUtil.getLineBreakCount(it.text) }
                }

                // TODO: Support K2 J2K in FUS
                J2KFusCollector.log(ConversionType.FILES, j2kKind == K1_NEW, conversionTime, linesCount, javaFiles.size)
            }

            // Perform user interaction first to avoid interrupting J2K in the middle of conversion and breaking "undo"
            val question = KotlinBundle.message("action.j2k.correction.required")
            val shouldProcessExternalCode = enableExternalCodeProcessing &&
                    (!askExternalCodeProcessing ||
                            Messages.showYesNoDialog(project, question, title, Messages.getQuestionIcon()) == Messages.YES)

            var newFiles: List<KtFile> = emptyList()

            // We execute a single command with the following steps:
            //
            // * Run Java to Kotlin converter, including the post-processings
            // * Find external usages that may need to be updated (part 1)
            // * Create new Kotlin files in a transparent global write action
            // * Prepare external code processing in a read action (part 2)
            // * Update external usages in a transparent global write action
            //
            // "Transparent" means that it will not be considered as a separate step for undo/redo purposes,
            // so when you undo a J2K conversion, it undoes the whole outermost command at once.
            //
            // "Global" means that you can undo it from any changed file: the converted files,
            // or the external files that were updated.
            project.executeCommand(KotlinBundle.message("action.j2k.task.name")) {
                if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
                        { convertWithStatistics() },
                        title, /* canBeCanceled = */ true,
                        project
                    )) return@executeCommand

                val result = converterResult ?: return@executeCommand
                val externalCodeProcessing = result.externalCodeProcessing
                val externalCodeUpdate = prepareExternalCodeUpdate(project, externalCodeProcessing, shouldProcessExternalCode)

                newFiles = project.runUndoTransparentGlobalWriteAction {
                    saveResults(javaFiles, result.results)
                        .map { it.toPsiFile(project) as KtFile }
                        .onEach { it.commitAndUnblockDocument() }
                }

                val contextElement = newFiles.firstOrNull() ?: return@executeCommand
                allowAnalysisOnEdt {
                    analyze(contextElement) {
                        externalCodeProcessing?.bindJavaDeclarationsToConvertedKotlinOnes(newFiles)
                    }
                }

                project.runUndoTransparentGlobalWriteAction {
                    externalCodeUpdate?.invoke()
                    PsiDocumentManager.getInstance(project).commitAllDocuments()
                    newFiles.singleOrNull()?.let {
                        FileEditorManager.getInstance(project).openFile(it.virtualFile, /* focusEditor = */ true)
                    }
                }
            }

            return newFiles
        }

        private fun prepareExternalCodeUpdate(project: Project, processing: ExternalCodeProcessing?, isEnabled: Boolean): (() -> Unit)? {
            if (!isEnabled || processing == null) return null

            var result: (() -> Unit)? = null
            ProgressManager.getInstance().runProcessWithProgressSynchronously({
                runReadAction {
                    result = processing.prepareWriteOperation(ProgressManager.getInstance().progressIndicator!!)
                }
            }, title, /* canBeCanceled = */ true, project)

            return result
        }

        private fun <T> Project.runUndoTransparentGlobalWriteAction(command: () -> T): T =
            CommandProcessor.getInstance().withUndoTransparentAction().use {
                CommandProcessor.getInstance().markCurrentCommandAsGlobal(this)
                runWriteAction {
                    command()
                }
            }

        private fun saveResults(javaFiles: List<PsiJavaFile>, convertedTexts: List<String>): List<VirtualFile> {
            fun uniqueKotlinFileName(javaFile: VirtualFile): String {
                val nioFile = javaFile.fileSystem.getNioPath(javaFile)

                var i = 0
                while (true) {
                    val fileName = javaFile.nameWithoutExtension + (if (i > 0) i else "") + ".kt"
                    if (nioFile == null || nioFile.resolveSibling(fileName).notExists()) return fileName
                    i++
                }
            }

            val result = ArrayList<VirtualFile>()
            for ((psiFile, text) in javaFiles.zip(convertedTexts)) {
                try {
                    val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile)
                    val errorMessage = when {
                        document == null -> KotlinBundle.message("action.j2k.error.cant.find.document", psiFile.name)
                        !document.isWritable -> KotlinBundle.message("action.j2k.error.read.only", psiFile.name)
                        else -> null
                    }
                    if (errorMessage != null) {
                        val message = KotlinBundle.message("action.j2k.error.cant.save.result", errorMessage)
                        MessagesEx.error(psiFile.project, message).showLater()
                        continue
                    }
                    document!!.replaceString(0, document.textLength, text)
                    FileDocumentManager.getInstance().saveDocument(document)

                    val virtualFile = psiFile.virtualFile
                    if (ScratchRootType.getInstance().containsFile(virtualFile)) {
                        val mapping = ScratchFileService.getInstance().scratchesMapping
                        mapping.setMapping(virtualFile, KotlinFileType.INSTANCE.language)
                    } else {
                        val fileName = uniqueKotlinFileName(virtualFile)
                        virtualFile.putUserData(pathBeforeJavaToKotlinConversion, virtualFile.path)
                        virtualFile.rename(this, fileName)
                    }
                    result += virtualFile
                } catch (e: IOException) {
                    MessagesEx.error(psiFile.project, e.message ?: "").showLater()
                }
            }
            return result
        }

        /**
         * For binary compatibility with third-party plugins.
         */
        fun convertFiles(
            files: List<PsiJavaFile>,
            project: Project,
            module: Module,
            enableExternalCodeProcessing: Boolean = true,
            askExternalCodeProcessing: Boolean = true,
            forceUsingOldJ2k: Boolean = false
        ): List<KtFile> {
            return convertFiles(
                files,
                project,
                module,
                enableExternalCodeProcessing,
                askExternalCodeProcessing,
                forceUsingOldJ2k,
                defaultSettings
            )
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = CommonDataKeys.PROJECT.getData(e.dataContext) ?: return
        val module = e.getData(PlatformCoreDataKeys.MODULE) ?: return
        val javaFiles = getSelectedWritableJavaFiles(e)
        if (javaFiles.isEmpty()) {
            showNothingToConvertErrorMessage(project)
            return
        }
        val j2kKind = getJ2kKind()
        val j2kConverterExtension = J2kConverterExtension.extension(j2kKind)
        if (shouldSkipConversionOfErroneousCode(javaFiles, project)) return
        if (j2kConverterExtension.doCheckBeforeConversion(project, module)) {
            Handler.convertFiles(javaFiles, project, module)
        } else {
            j2kConverterExtension.setUpAndConvert(project, module, javaFiles, Handler::convertFiles)
        }
    }

    private fun getSelectedWritableJavaFiles(e: AnActionEvent): List<PsiJavaFile> {
        val virtualFilesAndDirectories = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return emptyList()
        val project = e.project ?: return emptyList()
        val psiManager = PsiManager.getInstance(project)
        return getAllFilesRecursively(virtualFilesAndDirectories)
            .asSequence()
            .mapNotNull { psiManager.findFile(it) as? PsiJavaFile }
            .filter { it.fileType == JavaFileType.INSTANCE } // skip .jsp files
            .filter { it.isWritable }
            .toList()
    }

    private fun showNothingToConvertErrorMessage(project: Project) {
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(KotlinBundle.message("action.j2k.error.nothing.to.convert"), MessageType.ERROR, null)
            .createBalloon()
            .showInCenterOf(statusBar.component)
    }

    private fun shouldSkipConversionOfErroneousCode(javaFiles: List<PsiJavaFile>, project: Project): Boolean {
        val firstSyntaxError = javaFiles.asSequence().map { it.findDescendantOfType<PsiErrorElement>() }.firstOrNull() ?: return false
        val count = javaFiles.count { PsiTreeUtil.hasErrorElements(it) }
        assert(count > 0)
        val firstFileName = firstSyntaxError.containingFile.name
        val question = when (count) {
            1 -> KotlinBundle.message("action.j2k.correction.errors.single", firstFileName)
            else -> KotlinBundle.message("action.j2k.correction.errors.multiple", firstFileName, count - 1)
        }
        val okText = KotlinBundle.message("action.j2k.correction.investigate")
        val cancelText = KotlinBundle.message("action.j2k.correction.proceed")

        if (Messages.showOkCancelDialog(project, question, Handler.title, okText, cancelText, Messages.getWarningIcon()) == Messages.OK) {
            activateFileWithPsiElement(firstSyntaxError.navigationElement)
            return true
        }

        return false
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = isEnabled(e)
    }

    private fun isEnabled(e: AnActionEvent): Boolean {
        if (KotlinPlatformUtils.isCidr) return false
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return false
        val project = e.project ?: return false
        if (project.isDisposed) return false
        if (e.getData(PlatformCoreDataKeys.MODULE) == null) return false

        fun isWritableJavaFile(file: VirtualFile): Boolean {
            val psiManager = PsiManager.getInstance(project)
            return file.extension == JavaFileType.DEFAULT_EXTENSION && psiManager.findFile(file) is PsiJavaFile && file.isWritable
        }

        fun isWritablePackageDirectory(file: VirtualFile): Boolean {
            val directory = file.toPsiDirectory(project) ?: return false
            return PsiDirectoryFactory.getInstance(project).isPackage(directory) && file.isWritable
        }

        if (e.place != PROJECT_VIEW_POPUP && files.any(::isWritablePackageDirectory)) {
            // If a package is selected, we consider that it may contain Java files,
            // but don't actually check, because this check is recursive and potentially expensive: KTIJ-12688.
            //
            // This logic is disabled for the project view popup to avoid cluttering it.
            return true
        }

        return files.any(::isWritableJavaFile)
    }
}

private fun getJ2kKind(forceUsingOldJ2k: Boolean = false): J2kConverterExtension.Kind = when {
    isK2Mode() -> K2
    forceUsingOldJ2k || !NewJ2k.isEnabled -> K1_OLD
    else -> K1_NEW
}

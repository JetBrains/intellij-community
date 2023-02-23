// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.testIntegration

import com.intellij.CommonBundle
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.util.PsiUtil
import com.intellij.testIntegration.createTest.CreateTestAction
import com.intellij.testIntegration.createTest.CreateTestUtils.computeTestRoots
import com.intellij.testIntegration.createTest.TestGenerators
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.actions.JavaToKotlinAction
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.util.runWhenSmart
import org.jetbrains.kotlin.idea.base.util.runWithAlternativeResolveEnabled
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.getPackage
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.j2k.j2k
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.j2k.ConverterSettings.Companion.publicByDefault
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class KotlinCreateTestIntention : SelfTargetingRangeIntention<KtNamedDeclaration>(
    KtNamedDeclaration::class.java,
    KotlinBundle.lazyMessage("create.test")
) {
    override fun applicabilityRange(element: KtNamedDeclaration): TextRange? {
        if (element.hasExpectModifier() || element.nameIdentifier == null) return null
        val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return null
        if (module.platform.isJs() && !AdvancedSettings.getBoolean("kotlin.mpp.experimental")) return null

        if (element is KtClassOrObject) {
            if (element.isLocal) return null
            if (element is KtEnumEntry) return null
            if (element is KtClass && (element.isAnnotation() || element.isInterface())) return null

            if (element.resolveToDescriptorIfAny() == null) return null

            val virtualFile = PsiUtil.getVirtualFile(element)
            if (virtualFile == null || 
                ProjectRootManager.getInstance(element.project).fileIndex.isInTestSourceContent(virtualFile)) return null

            return TextRange(
                element.startOffset,
                element.getSuperTypeList()?.startOffset ?: element.body?.startOffset ?: element.endOffset
            )
        }

        if (element.parent !is KtFile) return null

        if (element is KtNamedFunction) {
            return TextRange((element.funKeyword ?: element.nameIdentifier!!).startOffset, element.nameIdentifier!!.endOffset)
        }

        if (element is KtProperty) {
            if (element.getter == null && element.delegate == null) return null
            return TextRange(element.valOrVarKeyword.startOffset, element.nameIdentifier!!.endOffset)
        }

        return null
    }

    override fun startInWriteAction() = false

    override fun applyTo(element: KtNamedDeclaration, editor: Editor?) {
        if (editor == null) throw IllegalArgumentException("This intention requires an editor")
        val lightClass = when (element) {
            is KtClassOrObject -> element.toLightClass()
            else -> element.containingKtFile.findFacadeClass()
        } ?: return

        object : CreateTestAction() {
            // Based on the com.intellij.testIntegration.createTest.JavaTestGenerator.createTestClass()
            private fun findTestClass(targetDirectory: PsiDirectory, className: String): PsiClass? {
                val psiPackage = targetDirectory.getPackage() ?: return null
                val scope = GlobalSearchScopesCore.directoryScope(targetDirectory, false)
                val klass = psiPackage.findClassByShortName(className, scope).firstOrNull() ?: return null
                if (!FileModificationService.getInstance().preparePsiElementForWrite(klass)) return null
                return klass
            }

            private fun getTempJavaClassName(project: Project, kotlinFile: VirtualFile): String {
                val baseName = kotlinFile.nameWithoutExtension
                val psiDir = kotlinFile.parent!!.toPsiDirectory(project)!!
                return generateSequence(0) { it + 1 }
                    .map { "$baseName$it" }
                    .first { psiDir.findFile("$it.java") == null && findTestClass(psiDir, it) == null }
            }

            // Based on the com.intellij.testIntegration.createTest.CreateTestAction.CreateTestAction.invoke()
            override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
                val srcModule = ModuleUtilCore.findModuleForPsiElement(element) ?: return
                val propertiesComponent = PropertiesComponent.getInstance()
                val testFolders = computeTestRoots(srcModule)
                if (testFolders.isEmpty() && !propertiesComponent.getBoolean("create.test.in.the.same.root")) {
                    if (Messages.showOkCancelDialog(
                            project,
                            KotlinBundle.message("test.integration.message.text.create.test.in.the.same.source.root"),
                            KotlinBundle.message("test.integration.title.no.test.roots.found"),
                            Messages.getQuestionIcon()
                        ) != Messages.OK
                    ) return

                    propertiesComponent.setValue("create.test.in.the.same.root", true)
                }

                val srcClass = getContainingClass(element) ?: return

                val srcDir = element.containingFile.containingDirectory
                val srcPackage = JavaDirectoryService.getInstance().getPackage(srcDir)

                val dialog = KotlinCreateTestDialog(project, text, srcClass, srcPackage, srcModule)
                if (!dialog.showAndGet()) return

                val existingClass = (findTestClass(dialog.targetDirectory, dialog.className) as? KtLightClass)?.kotlinOrigin
                if (existingClass != null) {
                    // TODO: Override dialog method when it becomes protected
                    val answer = Messages.showYesNoDialog(
                        project,
                        KotlinBundle.message("test.integration.message.text.kotlin.class", existingClass.name.toString()),
                        CommonBundle.getErrorTitle(),
                        KotlinBundle.message("test.integration.button.text.rewrite"),
                        KotlinBundle.message("test.integration.button.text.cancel"),
                        Messages.getErrorIcon()
                    )
                    if (answer == Messages.NO) return
                }

                val generatedClass = project.executeCommand(CodeInsightBundle.message("intention.create.test"), this) {
                    val generator = TestGenerators.INSTANCE.forLanguage(dialog.selectedTestFrameworkDescriptor.language)
                    project.runWithAlternativeResolveEnabled {
                        if (existingClass != null) {
                            dialog.explicitClassName = getTempJavaClassName(project, existingClass.containingFile.virtualFile)
                        }
                        generator.generateTest(project, dialog)
                    }
                } as? PsiClass ?: return

                project.runWhenSmart {
                    val generatedFile = generatedClass.containingFile as? PsiJavaFile ?: return@runWhenSmart

                    if (generatedClass.language == JavaLanguage.INSTANCE) {
                        project.executeCommand<Unit>(
                            KotlinBundle.message("convert.class.0.to.kotlin", generatedClass.name.toString()),
                            this
                        ) {
                            runWriteAction {
                                generatedClass.methods.forEach {
                                    it.throwsList.referenceElements.forEach { referenceElement -> referenceElement.delete() }
                                }
                            }

                            if (existingClass != null) {
                                runWriteAction {
                                    val existingMethodNames = existingClass
                                        .declarations
                                        .asSequence()
                                        .filterIsInstance<KtNamedFunction>()
                                        .mapTo(HashSet()) { it.name }
                                    generatedClass
                                        .methods
                                        .filter { it.name !in existingMethodNames }
                                        .forEach { it.j2k(settings = publicByDefault)
                                            ?.let { declaration -> existingClass.addDeclaration(declaration) } }
                                    generatedClass.delete()
                                }

                                NavigationUtil.activateFileWithPsiElement(existingClass)
                            } else {
                                with(PsiDocumentManager.getInstance(project)) {
                                    getDocument(generatedFile)?.let { doPostponedOperationsAndUnblockDocument(it) }
                                }

                                JavaToKotlinAction.convertFiles(
                                    listOf(generatedFile),
                                    project,
                                    srcModule,
                                    enableExternalCodeProcessing = false,
                                    forceUsingOldJ2k = true,
                                    settings = publicByDefault
                                ).singleOrNull()
                            }
                        }
                    }
                }
            }
        }.invoke(element.project, editor, lightClass)
    }
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsights.impl.base.testIntegration

import com.intellij.CommonBundle
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.FileModificationService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.model.SideEffectGuard
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.util.PsiUtil
import com.intellij.testIntegration.createTest.CreateTestAction
import com.intellij.testIntegration.createTest.CreateTestUtils.computeTestRoots
import com.intellij.testIntegration.createTest.TestGenerators
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.runWhenSmart
import org.jetbrains.kotlin.idea.base.util.runWithAlternativeResolveEnabled
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.psi.psiUtil.startOffset

abstract class AbstractKotlinCreateTestIntention : SelfTargetingRangeIntention<KtNamedDeclaration>(
    KtNamedDeclaration::class.java,
    KotlinBundle.lazyMessage("create.test")
) {

    protected abstract fun isResolvable(classOrObject: KtClassOrObject): Boolean

    protected abstract fun isApplicableForModule(module: Module): Boolean

    protected abstract fun convertClass(
        project: Project,
        generatedClass: PsiClass,
        existingClass: KtClassOrObject?,
        generatedFile: PsiFile,
        srcModule: Module
    )

    override fun applicabilityRange(element: KtNamedDeclaration): TextRange? {
        if (element.hasExpectModifier() || element.nameIdentifier == null) return null
        val module: Module = ModuleUtilCore.findModuleForPsiElement(element) ?: return null
        if (!isApplicableForModule(module)) return null

        return when {
            element is KtClassOrObject -> {
                if (element.isLocal) return null
                if (element is KtEnumEntry) return null
                if (element is KtClass && (element.isAnnotation() || element.isInterface())) return null

                if (!isResolvable(element)) return null

                val virtualFile = PsiUtil.getVirtualFile(element)
                if (virtualFile == null ||
                    ProjectRootManager.getInstance(element.project).fileIndex.isInTestSourceContent(virtualFile)) return null

                TextRange(
                    element.startOffset,
                    element.getSuperTypeList()?.startOffset ?: element.body?.startOffset ?: element.endOffset
                )
            }
            element.parent !is KtFile -> null
            element is KtNamedFunction -> {
                TextRange(
                    (element.funKeyword ?: element.nameIdentifier!!).startOffset,
                    element.nameIdentifier!!.endOffset
                )
            }
            element is KtProperty -> {
                if (element.getter == null && element.delegate == null) return null
                TextRange(element.valOrVarKeyword.startOffset, element.nameIdentifier!!.endOffset)
            }
            else -> null
        }
    }

    override fun startInWriteAction(): Boolean = false

    protected open fun getTempClassName(project: Project, existingClass: KtClassOrObject): String {
        val kotlinFile = existingClass.containingKtFile.virtualFile
        val baseName = kotlinFile.nameWithoutExtension
        val psiDir = kotlinFile.parent!!.toPsiDirectory(project)!!
        return generateSequence(0) { it + 1 }
            .map { "$baseName$it" }
            .first {
                psiDir.findFile("$it.java") == null &&
                        findTestClass(project, psiDir, it) == null
            }
    }

    // Based on the com.intellij.testIntegration.createTest.JavaTestGenerator.createTestClass()
    private fun findTestClass(project: Project, targetDirectory: PsiDirectory, className: String): PsiElement? {
        val psiPackage = JavaDirectoryService.getInstance()?.getPackage(targetDirectory) ?: return null
        val scope = GlobalSearchScopesCore.directoryScope(targetDirectory, false)

        findKtClassOrObject(className, project, scope)?.let { return it }

        val klass = psiPackage.findClassByShortName(className, scope).firstOrNull() ?: return null
        if (!FileModificationService.getInstance().preparePsiElementForWrite(klass)) return null
        return klass
    }

    override fun applyTo(element: KtNamedDeclaration, editor: Editor?) {
        val lightClass = when (element) {
            is KtClassOrObject -> element.toLightClass()
            else -> element.containingKtFile.findFacadeClass()
        } ?: return

        object : CreateTestAction() {
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
                    SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.SETTINGS)
                    propertiesComponent.setValue("create.test.in.the.same.root", true)
                }

                val srcClass = getContainingClass(element) ?: return

                val srcDir = element.containingFile.containingDirectory
                val srcPackage = JavaDirectoryService.getInstance().getPackage(srcDir)

                val dialog = KotlinCreateTestDialog(project, text, srcClass, srcPackage, srcModule)
                if (!dialog.showAndGet()) return

                val existingClass =
                    when(val findTestClass = findTestClass(project, dialog.targetDirectory, dialog.className)) {
                        is KtClassOrObject -> findTestClass
                        is PsiClass -> (findTestClass as? KtLightClass)?.kotlinOrigin
                        else -> null
                    }

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
                            dialog.explicitClassName = getTempClassName(project, existingClass)
                        }
                        generator.generateTest(project, dialog)
                    }
                } as? PsiClass ?: return

                project.runWhenSmart {
                    val containingFile = generatedClass.containingFile
                    val generatedFile = containingFile as? PsiJavaFile ?: return@runWhenSmart

                    convertClass(project, generatedClass, existingClass, generatedFile, srcModule)
                }
            }
        }.invoke(element.project, editor, lightClass)
    }
}

internal fun findKtClassOrObject(className: String, project: Project, scope: GlobalSearchScope): KtClassOrObject? {
    return KotlinClassShortNameIndex[className, project, scope].firstOrNull { ktClassOrObject ->
        FileModificationService.getInstance().preparePsiElementForWrite(ktClassOrObject)
    }
}
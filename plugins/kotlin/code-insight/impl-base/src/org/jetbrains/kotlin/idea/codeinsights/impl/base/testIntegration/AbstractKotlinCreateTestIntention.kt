// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsights.impl.base.testIntegration

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

abstract class AbstractKotlinCreateTestIntention : SelfTargetingRangeIntention<KtElement>(
    KtElement::class.java,
    KotlinBundle.messagePointer("create.test")
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

    @Deprecated("Kept for Backward compatibility, please override `applicabilityRange` function", level = DeprecationLevel.ERROR)
    fun applicabilityRange(element: KtNamedDeclaration): TextRange? =
        calculateApplicabilityRange(element)

    override fun applicabilityRange(element: KtElement): TextRange? =
        calculateApplicabilityRange(element)

    private fun calculateApplicabilityRange(element: KtElement): TextRange? {
        when (element) {
            is KtNamedDeclaration -> if (element.hasExpectModifier() || element.nameIdentifier == null) return null
            is KtFile -> {} // nothing
            else -> return null
        }
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
            element is KtFile -> {
                // offer to create a test for a kotlin file only when there are top-level functions / properties
                if (element.declarations.none { it is KtNamedFunction || it is KtProperty }) return null
                TextRange(element.startOffset, element.endOffset)
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

    override fun applyTo(element: KtElement, editor: Editor?) {
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

                val containingFile = element.containingFile
                val lightClass = when (element) {
                    is KtClassOrObject -> element.toLightClass()
                    is KtElement -> (containingFile as? KtFile)?.findFacadeClass()
                    else -> null
                } ?: return
                val srcClass = getContainingClass(lightClass) ?: return

                val srcDir = containingFile.containingDirectory
                val srcPackage = JavaDirectoryService.getInstance().getPackage(srcDir)

                val dialog = KotlinCreateTestDialog(project, text, element as? KtElement,srcClass, srcPackage, srcModule)
                if (!dialog.showAndGet()) return

                val existingClass =
                    when(val findTestClass = findTestClass(project, dialog.targetDirectory, dialog.className)) {
                        is KtClassOrObject -> findTestClass
                        is PsiClass -> (findTestClass as? KtLightClass)?.kotlinOrigin
                        else -> null
                    }

                val generatedClass = project.executeCommand(CodeInsightBundle.message("intention.create.test"), this) {
                    TestGenerators.INSTANCE.allForLanguageWithDefault(dialog.selectedTestFrameworkDescriptor.language)
                        .firstNotNullOfOrNull { generator ->
                            project.runWithAlternativeResolveEnabled {
                                if (existingClass != null) {
                                    dialog.explicitClassName = getTempClassName(project, existingClass)
                                }
                                val generateTest = generator.generateTest(project, dialog)
                                generateTest
                            }
                        }
                } as? PsiClass ?: return

                project.runWhenSmart {
                    val containingGeneratedFile = generatedClass.containingFile
                    val generatedFile = containingGeneratedFile as? PsiJavaFile ?: return@runWhenSmart

                    convertClass(project, generatedClass, existingClass, generatedFile, srcModule)
                }
            }
        }.invoke(element.project, editor, element)
    }
}

internal fun findKtClassOrObject(className: String, project: Project, scope: GlobalSearchScope): KtClassOrObject? {
    return KotlinClassShortNameIndex[className, project, scope].firstOrNull { ktClassOrObject ->
        FileModificationService.getInstance().preparePsiElementForWrite(ktClassOrObject)
    }
}
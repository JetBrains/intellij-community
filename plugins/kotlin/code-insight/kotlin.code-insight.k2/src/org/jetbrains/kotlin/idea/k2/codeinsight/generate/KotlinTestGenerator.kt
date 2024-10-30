// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.generate

import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.refactoring.util.classMembers.MemberInfo
import com.intellij.testIntegration.TestFramework
import com.intellij.testIntegration.TestIntegrationUtils
import com.intellij.testIntegration.TestIntegrationUtils.MethodKind
import com.intellij.testIntegration.createTest.CreateTestDialog
import com.intellij.testIntegration.createTest.TestGenerator
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.insertMembersAfter
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.psi.*
import java.util.*

class KotlinTestGenerator: TestGenerator {
    override fun generateTest(project: Project, d: CreateTestDialog): PsiElement? {
        return PostprocessReformattingAspect.getInstance(project).postponeFormattingInside<PsiElement?> {
            runWriteAction {
                try {
                    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()

                    val targetClass = createTestClass(d) as? KtClass ?: return@runWriteAction null
                    val frameworkDescriptor = d.selectedTestFrameworkDescriptor
                    val defaultSuperClass = frameworkDescriptor.getDefaultSuperClass()
                    d.getSuperClassName().takeIf { !Comparing.strEqual(it, defaultSuperClass) }?.let { superClassName ->
                        addSuperClass(targetClass, project, superClassName)
                    }

                    val file = targetClass.containingFile
                    val editor = CodeInsightUtil.positionCursor(
                        project,
                        file,
                        targetClass.body?.lBrace ?: targetClass
                    ) ?: return@runWriteAction null

                    addTestMethods(
                        editor,
                        targetClass,
                        d.targetClass,
                        frameworkDescriptor,
                        d.selectedMethods,
                        d.shouldGeneratedBefore(),
                        d.shouldGeneratedAfter()
                    )

                    return@runWriteAction targetClass
                } catch (_: IncorrectOperationException) {
                    showErrorLater(project, d.className)
                    return@runWriteAction null
                }
            }
        }
    }

    override fun toString(): String = KotlinBundle.message("intention.create.test.dialog.kotlin")

    companion object {
        private fun createTestClass(d: CreateTestDialog): PsiElement? {
            val testFrameworkDescriptor = d.selectedTestFrameworkDescriptor
            val fileTemplateDescriptor = MethodKind.TEST_CLASS.getFileTemplateDescriptor(testFrameworkDescriptor)
            val targetDirectory = d.targetDirectory

            val aPackage = JavaDirectoryService.getInstance().getPackage(targetDirectory)
            val className = d.className
            var targetFile: KtFile? = null
            if (aPackage != null) {
                val scope = GlobalSearchScopesCore.directoryScope(targetDirectory, false)
                targetFile = aPackage.getFiles(scope).firstOrNull { file ->
                    file is KtFile && file.name == "$className.${KotlinFileType.EXTENSION}"
                } as? KtFile
                // TODO: it could be that file is exists, but there is no such class declaration in it
            }
            if (targetFile == null && fileTemplateDescriptor != null) {
                val element = createTestClassFromCodeTemplate(d, fileTemplateDescriptor, targetDirectory)
                if (element is KtClass) return element
                targetFile = element as? KtFile
            }

            return targetFile?.declarations?.firstOrNull { it is KtClass && it.name == className }?.let { return it }
        }

        private fun createTestClassFromCodeTemplate(
            d: CreateTestDialog,
            fileTemplateDescriptor: FileTemplateDescriptor,
            targetDirectory: PsiDirectory
        ): KtElement? {
            val templateName = fileTemplateDescriptor.fileName
            val project = targetDirectory.getProject()
            val fileTemplate = FileTemplateManager.getInstance(project).getCodeTemplate(templateName)
            val defaultProperties = FileTemplateManager.getInstance(project).getDefaultProperties()
            val properties = Properties(defaultProperties)
            properties.setProperty(FileTemplate.ATTRIBUTE_NAME, d.className)
            val targetClass = d.targetClass
            if (targetClass != null && targetClass.isValid()) {
                properties.setProperty(FileTemplate.ATTRIBUTE_CLASS_NAME, targetClass.getQualifiedName())
            }
            val fileName = d.className + KotlinFileType.DOT_DEFAULT_EXTENSION
            return try {
                val createdFromTemplate = FileTemplateUtil.createFromTemplate(fileTemplate, fileName, properties, targetDirectory)
                createdFromTemplate as? KtElement
            } catch (_: Exception) {
                null
            }
        }

        private fun addSuperClass(targetClass: KtClass, project: Project, superClassName: String) {
            val extendsList = targetClass.superTypeListEntries
            val referenceElements = extendsList.flatMap { it.references.toList() }
            val psiFactory = KtPsiFactory(project)
            val superTypeEntry = psiFactory.createSuperTypeEntry(superClassName)
            if (referenceElements.isEmpty()) {
                val superTypeListEntry =
                    targetClass.addSuperTypeListEntry(superTypeEntry)
                shortenReferences(superTypeListEntry)
            } else {
                referenceElements[0]!!.element.replace(superTypeEntry)
            }
        }

        @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
        fun addTestMethods(
            editor: Editor,
            targetClass: KtClass,
            sourceClass: PsiClass?,
            descriptor: TestFramework,
            methods: Collection<out MemberInfo>,
            generateBefore: Boolean,
            generateAfter: Boolean
        ) {
            val existingNames: MutableSet<String?> = HashSet<String?>()
            var anchor: KtNamedFunction? = null
            if (generateBefore && descriptor.findSetUpMethod(targetClass) == null) {
                anchor = KotlinGenerateTestSupportActionBase.doGenerate(editor, targetClass.containingFile, targetClass, descriptor, MethodKind.SET_UP)
            }

            if (generateAfter && descriptor.findTearDownMethod(targetClass) == null) {
                anchor = KotlinGenerateTestSupportActionBase.doGenerate(editor, targetClass.containingFile, targetClass, descriptor, MethodKind.TEAR_DOWN)
            }

            val template = TestIntegrationUtils.createTestMethodTemplate(
                MethodKind.TEST, descriptor,
                targetClass, sourceClass, null, true, existingNames
            )
            val prefix = try {
                KtPsiFactory.contextual(targetClass).createFunction(template.getTemplateText()).getName() ?: ""
            } catch (_: IncorrectOperationException) {
                ""
            }

            for (existingMethod in targetClass.declarations.filterIsInstance<KtNamedFunction>()) {
                val name = existingMethod.getName() ?: continue
                existingNames.add(StringUtil.decapitalize(name.removePrefix(prefix)))
            }

            for (m in methods) {
                anchor = generateMethod(descriptor, targetClass, sourceClass, editor, m.getMember().getName(),
                                        existingNames, anchor)
            }
        }

        private fun showErrorLater(project: Project, targetClassName: String) {
            ApplicationManager.getApplication().invokeLater(Runnable {
                if (project.isDisposed) return@Runnable
                Messages.showErrorDialog(project,
                                         KotlinBundle.message("intention.error.cannot.create.class.message", targetClassName),
                                         KotlinBundle.message("intention.error.cannot.create.class.title"))
            })
        }

        private fun generateMethod(
            descriptor: TestFramework?,
            targetClass: KtClass,
            sourceClass: PsiClass?,
            editor: Editor,
            name: String?,
            existingNames: MutableSet<in String?>,
            anchor: KtNamedFunction?
        ): KtNamedFunction? {
            val project = targetClass.getProject()
            val psiFactory = KtPsiFactory(project)
            val dummyFunction = psiFactory.createFunction("fun dummy() = Unit")
            val function = insertMembersAfter(editor, targetClass, listOf(dummyFunction), anchor).firstOrNull()?.element ?: return null
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument())
            TestIntegrationUtils.runTestMethodTemplate(MethodKind.TEST,
                                                       descriptor, editor,
                                                       targetClass, sourceClass, function,
                                                       function.modifierList ?: function,
                                                       name,
                                                       true,
                                                       existingNames)
            return function
        }
    }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.createFromUsage

import com.intellij.ide.util.DirectoryChooserUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.caches.PerModulePackageCacheService
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.idea.refactoring.chooseContainer.SeparateFileWrapper
import org.jetbrains.kotlin.idea.refactoring.chooseContainer.chooseContainerElementIfNecessary
import org.jetbrains.kotlin.idea.refactoring.ui.CreateKotlinClassDialog
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.sourceRoot
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.isDotReceiver
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

enum class ClassKind(@NonNls val keyword: String, @Nls val description: String) {
    PLAIN_CLASS("class", KotlinBundle.message("text.class")),
    ENUM_CLASS("enum class", KotlinBundle.message("text.enum")),
    ENUM_ENTRY("", KotlinBundle.message("text.enum.constant")),
    ANNOTATION_CLASS("annotation class", KotlinBundle.message("text.annotation")),
    INTERFACE("interface", KotlinBundle.message("text.interface")),
    OBJECT("object", KotlinBundle.message("text.object")),
    DEFAULT("", ""); // Used as a placeholder and must be replaced with one of the kinds above
}

object CreateClassUtil {
    fun createClassDeclaration(
        project: Project,
        paramList: String,
        returnTypeString: String,
        kind: ClassKind,
        name: String,
        applicableParents: List<PsiElement>,
        open: Boolean,
        inner: Boolean,
        isInsideInnerOrLocalClass: Boolean,
        primaryConstructorVisibilityModifier: String?
    ): KtClassOrObject {
        val psiFactory = KtPsiFactory(project)
        val classBody = when (kind) {
            ClassKind.ANNOTATION_CLASS, ClassKind.ENUM_ENTRY -> ""
            else -> "{\n\n}"
        }
        val safeName = name.quoteIfNeeded()
        return when (kind) {
            ClassKind.ENUM_ENTRY -> {
                val targetParent = applicableParents.singleOrNull()
                if (!(targetParent is KtClass && targetParent.isEnum())) {
                    throw KotlinExceptionWithAttachments("Enum class expected: ${targetParent?.let { it::class.java }}")
                        .withPsiAttachment("targetParent", targetParent)
                }
                val hasParameters = targetParent.primaryConstructorParameters.isNotEmpty()
                psiFactory.createEnumEntry("$safeName${if (hasParameters) "()" else " "}")
            }

            else -> {
                val openMod = if (open && kind != ClassKind.INTERFACE) "open " else ""
                val innerMod = if (inner || isInsideInnerOrLocalClass) "inner " else ""
                val typeParamList = when (kind) {
                    ClassKind.PLAIN_CLASS, ClassKind.INTERFACE -> "<>"
                    else -> ""
                }
                val ctor = primaryConstructorVisibilityModifier?.let { " $it constructor" } ?: ""
                psiFactory.createDeclaration(
                    "$openMod$innerMod${kind.keyword} $safeName$typeParamList$ctor$paramList$returnTypeString $classBody"
                )
            }
        }
    }

    fun chooseAndCreateClass(
        project: Project,
        editor: Editor,
        file: KtFile,
        psiElement: KtElement?,
        classKind: ClassKind,
        initialApplicableParents: List<PsiElement>,
        className: String,
        commandName: String,
        runCreateClassBuilder: (PsiElement) -> Unit
    ) {
        val applicableParents = mutableListOf<PsiElement>()
        initialApplicableParents.filterNotTo(applicableParents) { element ->
            element is KtClassOrObject && element.superTypeListEntries.any {
                when (it) {
                    is KtDelegatedSuperTypeEntry, is KtSuperTypeEntry -> it.typeAsUserType == psiElement
                    is KtSuperTypeCallEntry -> it == psiElement
                    else -> false
                }
            }
        }

        if (classKind != ClassKind.ENUM_ENTRY && applicableParents.find { it is PsiPackage } == null) {
            applicableParents += SeparateFileWrapper(PsiManager.getInstance(project))
        }

        if (isUnitTestMode()) {
            val targetParent = applicableParents.firstOrNull { element ->
                if (element is PsiPackage) false else element.allChildren.any { it is PsiComment && it.text == "// TARGET_PARENT:" }
            } ?: initialApplicableParents.last()
            showCreateClassDialogAndRunBuilder(targetParent, file, classKind, className, commandName, runCreateClassBuilder)
        }
        else {
            chooseContainerElementIfNecessary(
                applicableParents.reversed(),
                editor,
                KotlinBundle.message("choose.class.container"),
                true, onSelect = { targetParent -> showCreateClassDialogAndRunBuilder(targetParent, file, classKind, className, commandName, runCreateClassBuilder)}
            )
        }
    }

    private fun showCreateClassDialogAndRunBuilder(
        selectedParent: PsiElement,
        file: KtFile,
        classKind: ClassKind,
        className: String,
        commandName: String,
        runCreateClassBuilder: (PsiElement) -> Unit
    ) {
        var targetFile:PsiElement = selectedParent
        if (selectedParent is SeparateFileWrapper) {
            if (isUnitTestMode()) {
                targetFile = file
            }
            else {
                val ideaClassKind = classKind.toIdeaClassKind()
                val defaultPackageFqName = file.packageFqName
                val dialog = object : CreateKotlinClassDialog(
                    file.project,
                    KotlinBundle.message("create.0", ideaClassKind.description),
                    className,
                    defaultPackageFqName.asString(),
                    ideaClassKind,
                    false,
                    file.module
                ) {
                    override fun reportBaseInSourceSelectionInTest() = true
                }
                dialog.show()
                if (dialog.exitCode != DialogWrapper.OK_EXIT_CODE) return

                val targetDirectory = dialog.targetDirectory ?: return
                val fileName = "$className.${KotlinFileType.EXTENSION}"
                val packageFqName = targetDirectory.getFqNameWithImplicitPrefix()

                file.project.executeWriteCommand(commandName) {
                    targetFile = getOrCreateKotlinFile(fileName, targetDirectory, (packageFqName ?: defaultPackageFqName).asString())
                }
            }
        }

        WriteCommandAction.runWriteCommandAction(file.project, Computable {
            val targetParent =
                when (targetFile) {
                    is KtElement, is PsiClass -> targetFile
                    is PsiPackage -> createFileByPackage(className, targetFile as PsiPackage, file)
                    else -> throw KotlinExceptionWithAttachments("Unexpected element: ${targetFile::class.java}")
                        .withPsiAttachment("selectedParent", targetFile)
                }
            if (targetParent != null) {
                runCreateClassBuilder(targetParent)
            }
        })
    }

    private fun ClassKind.toIdeaClassKind() = com.intellij.codeInsight.daemon.impl.quickfix.ClassKind { description.capitalize() }

    private fun PsiDirectory.getPackage(): PsiPackage? = JavaDirectoryService.getInstance()!!.getPackage(this)

    private fun PsiDirectory.getNonRootFqNameOrNull(): FqName? = getPackage()?.qualifiedName?.let(::FqName)

    private fun PsiDirectory.getFqNameWithImplicitPrefix(): FqName? {
        val packageFqName = getNonRootFqNameOrNull() ?: return null
        sourceRoot?.takeIf { !it.hasExplicitPackagePrefix(project) }?.let { sourceRoot ->
            @OptIn(K1ModeProjectStructureApi::class)
            val implicitPrefix = PerModulePackageCacheService.getInstance(project).getImplicitPackagePrefix(sourceRoot)
            return FqName.fromSegments((implicitPrefix.pathSegments() + packageFqName.pathSegments()).map { it.asString() })
        }

        return packageFqName
    }
    private fun VirtualFile.hasExplicitPackagePrefix(project: Project): Boolean =
        toPsiDirectory(project)?.getPackage()?.qualifiedName?.isNotEmpty() == true

    private fun getOrCreateKotlinFile(
        fileName: String,
        targetDir: PsiDirectory,
        packageName: String? = targetDir.getFqNameWithImplicitPrefix()?.asString()
    ): KtFile =
        (targetDir.findFile(fileName) ?: createKotlinFile(fileName, targetDir, packageName)) as KtFile

    private fun createKotlinFile(
        fileName: String,
        targetDir: PsiDirectory,
        packageName: String? = targetDir.getFqNameWithImplicitPrefix()?.asString()
    ): KtFile {
        targetDir.checkCreateFile(fileName)
        val packageFqName = packageName?.let(::FqName) ?: FqName.ROOT
        val file = PsiFileFactory.getInstance(targetDir.project).createFileFromText(
            fileName, KotlinFileType.INSTANCE, if (!packageFqName.isRoot) "package ${packageFqName.quoteIfNeeded()} \n\n" else ""
        )
        return targetDir.add(file) as KtFile
    }

    private fun createFileByPackage(
        name: String,
        psiPackage: PsiPackage,
        originalFile: KtFile
    ): KtFile? {
        val directories = psiPackage.directories.filter { it.canRefactorElement() }
        assert(directories.isNotEmpty()) {
            "Package '${psiPackage.qualifiedName}' must be refactorable"
        }

        val currentModule = ModuleUtilCore.findModuleForPsiElement(originalFile)
        val preferredDirectory =
            directories.firstOrNull { ModuleUtilCore.findModuleForPsiElement(it) == currentModule }
                ?: directories.firstOrNull()

        val targetDirectory = if (directories.size > 1 && !isUnitTestMode()) {
            DirectoryChooserUtil.chooseDirectory(directories.toTypedArray(), preferredDirectory, originalFile.project, HashMap())
        } else {
            preferredDirectory
        } ?: return null

        val fileName = "$name.${KotlinFileType.INSTANCE.defaultExtension}"
        val targetFile = getOrCreateKotlinFile(fileName, targetDirectory)
        return targetFile
    }

    fun getFullCallExpression(element: KtSimpleNameExpression): KtExpression? {
        return element.parent.let {
            when {
                it is KtCallExpression && it.calleeExpression == element -> null
                it is KtQualifiedExpression && it.selectorExpression == element -> it
                else -> element
            }
        }
    }
    fun isQualifierExpected(element: KtSimpleNameExpression): Boolean =
        element.isDotReceiver() || ((element.parent as? KtDotQualifiedExpression)?.isDotReceiver() ?: false)
    fun String.checkClassName(): Boolean = isNotEmpty() && Character.isUpperCase(first())



}

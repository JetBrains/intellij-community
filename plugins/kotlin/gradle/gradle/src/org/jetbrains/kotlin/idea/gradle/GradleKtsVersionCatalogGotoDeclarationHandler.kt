// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.*
import com.intellij.psi.util.parentOfType
import com.intellij.util.asSafely
import org.jetbrains.kotlin.idea.references.SyntheticPropertyAccessorReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.structuralsearch.resolveExprType
import org.jetbrains.kotlin.js.descriptorUtils.getKotlinTypeFqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.plainContent
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.plugins.gradle.service.project.CommonGradleProjectResolverExtension
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames
import org.jetbrains.plugins.gradle.toml.findOriginInTomlFile
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.getCapitalizedAccessorName
import java.nio.file.Path

class GradleKtsVersionCatalogGotoDeclarationHandler: GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        if (!Registry.`is`(CommonGradleProjectResolverExtension.GRADLE_VERSION_CATALOGS_DYNAMIC_SUPPORT, false)) {
            return null
        }
        if (sourceElement == null) {
            return null
        }

        val propertyAccessorRef = sourceElement.parentOfType<KtReferenceExpression>()
            ?.references
            ?.find { it is SyntheticPropertyAccessorReference }
            ?: return null

        val accessorMethod = propertyAccessorRef.resolve() as? PsiMethod ?: return null

        val settingsFile = getSettingsFile(sourceElement.project) ?: return null
        val visitor = SettingsKtsFileResolveVisitor(accessorMethod)

        settingsFile.accept(visitor)
        visitor.resolveTarget?.let { return arrayOf(it) }

        return findOriginInTomlFile(accessorMethod, sourceElement)?.let { arrayOf(it) }
    }
}

private fun getSettingsFile(project: Project) : KtFile? {
    val projectData = ProjectDataManager.getInstance()
        .getExternalProjectsData(project, GradleConstants.SYSTEM_ID).mapNotNull { it.externalProjectStructure }

    for (projectDatum in projectData) {
        val settings = Path.of(projectDatum.data.linkedExternalProjectPath).resolve(GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME).let {
            VfsUtil.findFile(it, false)
        }?.let { PsiManager.getInstance(project).findFile(it) }?.asSafely<KtFile>()
        return settings
    }
    return null
}

private class SettingsKtsFileResolveVisitor(val element : PsiMethod) : KtVisitorVoid(), PsiRecursiveVisitor {

    var resolveTarget : PsiElement? = null

    val accessorName = element
        .takeIf { (it.returnType as? PsiClassType)?.rawType()?.canonicalText == GradleCommonClassNames.GRADLE_API_PROVIDER_PROVIDER }
        ?.let(::getCapitalizedAccessorName)

    override fun visitElement(element: PsiElement) {
        element.acceptChildren(this)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        val funReturnType = expression.resolveExprType()?.getKotlinTypeFqName(false)
        val funName = (expression.referenceExpression() as? KtNameReferenceExpression)?.getReferencedName()

        if (element.name == funName && funReturnType == GradleCommonClassNames.GRADLE_API_VERSION_CATALOG_BUILDER) {
            resolveTarget = expression
            return
        }

        val psiMethod = expression.referenceExpression()?.mainReference?.resolve() as? PsiMethod

        if (accessorName != null && psiMethod?.containingClass?.qualifiedName == GradleCommonClassNames.GRADLE_API_VERSION_CATALOG_BUILDER) {
            val definedNameValue = (expression.valueArguments.firstOrNull()
                ?.getArgumentExpression() as? KtStringTemplateExpression)
                ?.plainContent
                ?: return super.visitCallExpression(expression)

            val longName = definedNameValue.split("_", ".", "-").joinToString("", transform = ::capitalize)
            if (longName == accessorName) {
                resolveTarget = expression
                return
            }
        }

        super.visitCallExpression(expression)
    }
}

fun capitalize(s: String): String {
    if (s.isEmpty()) return s
    if (s.length == 1) return StringUtil.toUpperCase(s)
    if (Character.isUpperCase(s[1])) return s
    val chars = s.toCharArray()
    chars[0] = chars[0].uppercaseChar()
    return String(chars)
}
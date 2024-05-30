// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.createFromUsage

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
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
        primaryConstructorName: String?
    ): KtNamedDeclaration {
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
                val ctor = primaryConstructorName?.let { " $it constructor" } ?: ""
                psiFactory.createDeclaration<KtClassOrObject>(
                    "$openMod$innerMod${kind.keyword} $safeName$typeParamList$ctor$paramList$returnTypeString $classBody"
                )
            }
        }
    }
}

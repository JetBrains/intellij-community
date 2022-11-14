// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.idea.core.setType
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.js.isJs
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperInterfaces
import org.jetbrains.kotlin.types.typeUtil.isBoolean
import org.jetbrains.kotlin.types.typeUtil.makeNullable

class NonExternalClassifierExtendingStateOrPropsInspection : AbstractKotlinInspection() {
    override fun buildVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): PsiElementVisitor = classOrObjectVisitor { classOrObject ->
        if (classOrObject.platform.isJs()) {
            val classDescriptor = classOrObject.descriptor as? ClassDescriptor ?: return@classOrObjectVisitor
            if (classDescriptor.implementsRProps || classDescriptor.implementsRState) {
                if (classOrObject is KtClass) {
                    if (classOrObject.isInterface() && !classDescriptor.isExternal) {
                        val nameIdentifier = classOrObject.nameIdentifier ?: return@classOrObjectVisitor
                        holder.registerProblem(nameIdentifier,
                                               KotlinBundle.message("interface.should.be.external"),
                                               AddExternalQuickFix)
                    } else {
                        val classKeyword = classOrObject.getClassKeyword() ?: return@classOrObjectVisitor
                        holder.registerProblem(classKeyword,
                                               KotlinBundle.message("class.should.be.external.interface"))
                    }
                }
                if (classOrObject is KtObjectDeclaration) {
                    val objectKeyword = classOrObject.getObjectKeyword() ?: return@classOrObjectVisitor
                    holder.registerProblem(objectKeyword,
                                           KotlinBundle.message("object.should.be.external.interface"))
                }
            }
        }
    }
}

class NonNullableBooleanPropertyInExternalInterfaceInspection : AbstractKotlinInspection() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor = declarationVisitor { declaration ->
        if (!declaration.platform.isJs()) return@declarationVisitor
        val parent = declaration.containingClassOrObject as? KtClass ?: return@declarationVisitor
        val property = declaration as? KtProperty ?: return@declarationVisitor
        val type = property.type() ?: return@declarationVisitor
        if (parent.isInterface() && parent.hasModifier(KtTokens.EXTERNAL_KEYWORD) && type.isBoolean() && !type.isMarkedNullable) {
            holder.registerProblem(property,
                                   KotlinBundle.message("boolean.property.in.external.interface.should.be.nullable"),
                                   ConvertToNullableTypeFix())
        }
    }
}

class NonVarPropertyInExternalInterfaceInspection : AbstractKotlinInspection() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor = declarationVisitor { declaration ->
        if (!declaration.platform.isJs()) return@declarationVisitor
        val parent = declaration.containingClassOrObject as? KtClass ?: return@declarationVisitor
        val property = declaration as? KtProperty ?: return@declarationVisitor
        val parentClassDescriptor = parent.descriptor as? ClassDescriptor ?: return@declarationVisitor
        val parentImplementsReactStateOrProps =
            parentClassDescriptor.implementsRProps || parentClassDescriptor.implementsRState
        if (parent.isInterface() && parent.hasModifier(KtTokens.EXTERNAL_KEYWORD) && parentImplementsReactStateOrProps && !property.isVar) {
            holder.registerProblem(
                property.valOrVarKeyword,
                KotlinBundle.message("property.in.external.interface.should.be.var"),
                IntentionWrapper(ChangeVariableMutabilityFix(property, true))
            )
        }
    }
}

private val REACT_PACKAGE_ID = Name.identifier("react")
private val REACT_PACKAGE = FqName(REACT_PACKAGE_ID.identifier)
private val R_PROPS = REACT_PACKAGE.child(Name.identifier("RProps"))
private val R_STATE = REACT_PACKAGE.child(Name.identifier("RState"))
private val STATE = REACT_PACKAGE.child(Name.identifier("State"))
private val PROPS = REACT_PACKAGE.child(Name.identifier("Props"))

private val ClassDescriptor.implementsRState: Boolean
    get() = getSuperInterfaces().any { it.fqNameSafe == STATE || it.fqNameSafe == R_STATE || it.implementsRState }

private val ClassDescriptor.implementsRProps: Boolean
    get() = getSuperInterfaces().any { it.fqNameSafe == PROPS || it.fqNameSafe == R_PROPS || it.implementsRProps }

object AddExternalQuickFix : LocalQuickFix {
    override fun getName(): String = KotlinBundle.message("add.external.keyword")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val psiElement = descriptor.psiElement.parent
        if (psiElement is KtClass) {
            psiElement.addModifier(KtTokens.EXTERNAL_KEYWORD)
        }
    }
}

class ConvertToNullableTypeFix : LocalQuickFix {
    override fun getName(): String = KotlinBundle.message("convert.to.nullable.type")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val parameter = descriptor.psiElement as? KtProperty ?: return
        val type = parameter.type() ?: return
        parameter.setType(type.makeNullable())
    }
}

private fun KtDeclaration.type() = (resolveToDescriptorIfAny() as? CallableDescriptor)?.returnType

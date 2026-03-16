// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.actions.generate

import com.intellij.java.JavaBundle
import com.intellij.openapi.ui.Messages
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.base.compilerPreferences.KotlinBaseCompilerConfigurationUiBundle
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.core.overrideImplement.BodyType
import org.jetbrains.kotlin.idea.core.overrideImplement.OverrideMemberChooserObject
import org.jetbrains.kotlin.idea.core.overrideImplement.generateMember
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassOrAny

@K1Deprecation
tailrec fun ClassDescriptor.findDeclaredFunction(
    name: String,
    checkSuperClasses: Boolean,
    filter: (FunctionDescriptor) -> Boolean
): FunctionDescriptor? {
    unsubstitutedMemberScope.getContributedFunctions(Name.identifier(name), NoLookupLocation.FROM_IDE)
        .firstOrNull { it.containingDeclaration == this && it.kind == CallableMemberDescriptor.Kind.DECLARATION && filter(it) }
        ?.let { return it }

    return if (checkSuperClasses) getSuperClassOrAny().findDeclaredFunction(name, checkSuperClasses = true, filter) else null
}

@K1Deprecation
fun getPropertiesToUseInGeneratedMember(classOrObject: KtClassOrObject): List<KtNamedDeclaration> {
    return ArrayList<KtNamedDeclaration>().apply {
        classOrObject.primaryConstructorParameters.filterTo(this) { it.hasValOrVar() }
        classOrObject.declarations.asSequence().filterIsInstance<KtProperty>().filterTo(this) {
            when (it.unsafeResolveToDescriptor()) {
                is ValueParameterDescriptor, is PropertyDescriptor -> true
                else -> false
            }
        }
    }.filter {
        it.name?.quoteIfNeeded().isIdentifier()
    }
}

private val MEMBER_RENDERER = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.withOptions {
    modifiers = emptySet()
    startFromName = true
    parameterNameRenderingPolicy = ParameterNameRenderingPolicy.NONE
}

@K1Deprecation
fun confirmMemberRewrite(targetClass: KtClassOrObject, vararg descriptors: FunctionDescriptor): Boolean {
    if (isUnitTestMode()) return true

    val functionsText =
        descriptors.joinToString(separator = " ${KotlinBaseCompilerConfigurationUiBundle.message("configuration.text.and")} ") { "'${MEMBER_RENDERER.render(it)}'" }
    val message = KotlinBundle.message("action.generate.functions.already.defined", functionsText, targetClass.name.toString())
    return Messages.showYesNoDialog(
        targetClass.project, message,
        JavaBundle.message("generate.equals.and.hashcode.already.defined.title"),
        Messages.getQuestionIcon()
    ) == Messages.YES
}

@K1Deprecation
fun generateFunctionSkeleton(descriptor: FunctionDescriptor, targetClass: KtClassOrObject): KtNamedFunction {
    return OverrideMemberChooserObject
        .create(targetClass.project, descriptor, descriptor, BodyType.FromTemplate)
        .generateMember(targetClass, false) as KtNamedFunction
}
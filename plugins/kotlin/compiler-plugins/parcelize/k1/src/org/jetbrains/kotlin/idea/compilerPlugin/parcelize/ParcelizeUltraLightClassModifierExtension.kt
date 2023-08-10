// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compilerPlugin.parcelize

import com.intellij.psi.PsiVariable
import com.intellij.psi.impl.light.LightFieldBuilder
import org.jetbrains.kotlin.asJava.UltraLightClassModifierExtension
import org.jetbrains.kotlin.asJava.builder.LightMemberOrigin
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtUltraLightClass
import org.jetbrains.kotlin.asJava.classes.createGeneratedMethodFromDescriptor
import org.jetbrains.kotlin.asJava.elements.KtLightField
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.parcelize.ParcelizeSyntheticComponent
import org.jetbrains.kotlin.parcelize.serializers.ParcelizeExtensionBase
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.util.isAnnotated
import org.jetbrains.kotlin.util.isOrdinaryClass

class ParcelizeUltraLightClassModifierExtension : ParcelizeExtensionBase, UltraLightClassModifierExtension {
    private fun tryGetParcelizeClass(declaration: KtDeclaration, descriptor: Lazy<DeclarationDescriptor?>): ClassDescriptor? {
        if (!declaration.isOrdinaryClass || !declaration.isAnnotated) {
            return null
        }

        val module = declaration.module
        if (module == null || !ParcelizeAvailability.isAvailable(module)) {
            return null
        }

        val descriptorValue = descriptor.value ?: return null
        val parcelizeClass = (descriptorValue as? ClassDescriptor) ?: descriptorValue.containingDeclaration as? ClassDescriptor

        if (parcelizeClass == null || !parcelizeClass.isParcelizeClassDescriptor) {
            return null
        }

        return parcelizeClass
    }

    override fun interceptFieldsBuilding(
        declaration: KtDeclaration,
        descriptor: Lazy<DeclarationDescriptor?>,
        containingDeclaration: KtUltraLightClass,
        fieldsList: MutableList<KtLightField>
    ) {
        val parcelizeClass = tryGetParcelizeClass(declaration, descriptor) ?: return
        if (parcelizeClass.hasCreatorField()) return

        val creatorType = "android.os.Parcelable.Creator<${parcelizeClass.fqNameSafe.asString()}>"
        val field = object: LightFieldBuilder("CREATOR", creatorType, containingDeclaration), KtLightField {
            override val kotlinOrigin: KtDeclaration? get() = null
            override val lightMemberOrigin: LightMemberOrigin? get() = null
            override fun computeConstantValue(visitedVars: MutableSet<PsiVariable>?): Any? = null
            override fun getContainingClass(): KtLightClass = containingDeclaration
        }.also {
            it.setModifiers("public", "static", "final")
        }

        fieldsList.add(field)
    }

    override fun interceptMethodsBuilding(
        declaration: KtDeclaration,
        descriptor: Lazy<DeclarationDescriptor?>,
        containingDeclaration: KtUltraLightClass,
        methodsList: MutableList<KtLightMethod>
    ) {
        val parcelizeClass = tryGetParcelizeClass(declaration, descriptor) ?: return

        with(parcelizeClass) {
            if (hasSyntheticDescribeContents()) {
                findFunction(ParcelizeSyntheticComponent.ComponentKind.DESCRIBE_CONTENTS)?.let {
                    methodsList.add(
                        containingDeclaration.createGeneratedMethodFromDescriptor(it)
                    )
                }
            }

            if (hasSyntheticWriteToParcel()) {
                findFunction(ParcelizeSyntheticComponent.ComponentKind.WRITE_TO_PARCEL)?.let {
                    methodsList.add(
                        containingDeclaration.createGeneratedMethodFromDescriptor(it)
                    )
                }
            }
        }
    }

}
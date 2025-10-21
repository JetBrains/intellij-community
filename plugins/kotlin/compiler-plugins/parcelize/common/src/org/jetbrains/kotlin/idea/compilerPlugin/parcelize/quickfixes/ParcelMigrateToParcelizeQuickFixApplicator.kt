// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.idea.base.psi.getOrCreateCompanionObject
import org.jetbrains.kotlin.idea.base.psi.getSingleUnwrappedStatementOrThis
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.parcelize.ParcelizeNames
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.addRemoveModifier.setModifierList
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.typeRefHelpers.setReceiverTypeReference

class ParcelMigrateToParcelizeQuickFixApplicator<CONTEXT>(
    private val resolver: ParcelMigrateToParcelizeResolver<CONTEXT>,
) {
    companion object {
        private val PARCELER_WRITE_FUNCTION_NAME = Name.identifier("write")
        private val PARCELER_CREATE_FUNCTION_NAME = Name.identifier("create")
        private val LOG = Logger.getInstance(ParcelMigrateToParcelizeQuickFixApplicator::class.java)
    }

    context(_: CONTEXT)
    private fun KtClass.findParcelerCompanionObject(): KtObjectDeclaration? = with(resolver) {
        companionObjects.firstOrNull { obj -> ParcelizeNames.PARCELER_CLASS_IDS.any { obj.hasSuperClass(it) } }
    }

    context(_: CONTEXT)
    private fun KtNamedFunction.doesLookLikeWriteToParcelOverride(): Boolean =
        looksLikeOverrideOf(
            ParcelizeNames.WRITE_TO_PARCEL_NAME,
            ParcelizeNames.PARCEL_ID, StandardClassIds.Int)

    context(_: CONTEXT)
    private fun KtNamedFunction.doesLookLikeDescribeContentsOverride(): Boolean =
        looksLikeOverrideOf(
            ParcelizeNames.DESCRIBE_CONTENTS_NAME,
            returnType = StandardClassIds.Int)

    context(_: CONTEXT)
    private fun KtNamedFunction.doesLookLikeNewArrayOverride(): Boolean =
        looksLikeOverrideOf(
            ParcelizeNames.NEW_ARRAY_NAME,
            StandardClassIds.Int,
            returnType = StandardClassIds.Array)

    context(_: CONTEXT)
    private fun KtNamedFunction.doesLookLikeCreateFromParcelOverride(): Boolean =
        looksLikeOverrideOf(
            ParcelizeNames.CREATE_FROM_PARCEL_NAME,
            ParcelizeNames.PARCEL_ID)

    context(_: CONTEXT)
    private fun KtClass.findWriteToParcelOverride() = findFunction { doesLookLikeWriteToParcelOverride() }

    context(_: CONTEXT)
    private fun KtClass.findDescribeContentsOverride() = findFunction { doesLookLikeDescribeContentsOverride() }

    context(_: CONTEXT)
    private fun KtObjectDeclaration.findNewArrayOverride() = findFunction { doesLookLikeNewArrayOverride() }

    context(_: CONTEXT)
    private fun KtClassOrObject.findCreateFromParcel() = findFunction { doesLookLikeCreateFromParcelOverride() }

    context(_: CONTEXT)
    private fun KtClass.findCreatorClass(): KtClassOrObject? = with(resolver) {
        for (companion in companionObjects) {
            if (companion.name == ParcelizeNames.CREATOR_NAME.identifier) {
                return companion
            }

            val creatorProperty = companion.declarations.asSequence()
                .filterIsInstance<KtProperty>()
                .firstOrNull { it.name == ParcelizeNames.CREATOR_NAME.identifier }
                ?: continue

            if (!creatorProperty.isJvmField) continue

            val initializer = creatorProperty.initializer ?: continue
            when (initializer) {
                is KtObjectLiteralExpression -> return initializer.objectDeclaration
                is KtCallExpression -> initializer.resolveToConstructedClass()?.let { return it }
            }
        }

        return null
    }

    context(_: CONTEXT)
    private fun KtNamedFunction.doesLookLikeWriteImplementation(): Boolean = with(resolver) {
        val containingParcelableClassFqName = containingClassOrObject?.containingClass()?.fqName ?: return false

        return looksLikeOverrideOf(
            PARCELER_WRITE_FUNCTION_NAME,
            ParcelizeNames.PARCEL_ID, StandardClassIds.Int,
            receiverTypeFqName = containingParcelableClassFqName)
    }

    context(_: CONTEXT)
    private fun KtNamedFunction.doesLookLikeCreateImplementation(): Boolean =
        looksLikeOverrideOf(
            PARCELER_CREATE_FUNCTION_NAME,
            ParcelizeNames.PARCEL_ID)

    context(_: CONTEXT)
    private fun KtObjectDeclaration.findCreateImplementation() = findFunction { doesLookLikeCreateImplementation() }

    context(_: CONTEXT)
    private fun KtObjectDeclaration.findWriteImplementation() = findFunction { doesLookLikeWriteImplementation() }

    context(_: CONTEXT)
    private fun KtNamedFunction.looksLikeOverrideOf(
        functionName: Name,
        vararg valueParameterClassIds: ClassId,
        returnType: ClassId? = null,
        receiverTypeFqName: FqName? = null,
    ): Boolean = with(resolver) {
        if (name != functionName.identifier) {
            return false
        }
        if (returnType != null && returnTypeClassId != returnType) {
            return false
        }
        if (valueParameters.size != valueParameterClassIds.size) {
            return false
        }
        for (i in 0 until valueParameters.size) {
            if (valueParameters[i].returnTypeClassId != valueParameterClassIds[i]) {
                return false
            }
        }
        if (receiverTypeFqName != null) {
            if (receiverTypeClassId?.asSingleFqName() != receiverTypeFqName) {
                return false
            }
        } else {
            if (receiverTypeReference != null) {
                return false
            }
        }

        return true
    }

    private fun KtClassOrObject.findFunction(f: KtNamedFunction.() -> Boolean) =
        declarations.asSequence().filterIsInstance<KtNamedFunction>().firstOrNull(f)

    context(_: CONTEXT)
    fun prepare(parcelableClass: KtClass): PreparedAction = with(resolver) {
        val parcelerObject = parcelableClass.findParcelerCompanionObject()
        val parcelerOrCompanion = parcelerObject ?: parcelableClass.companionObjects.firstOrNull()

        val oldWriteToParcelFunction = parcelableClass.findWriteToParcelOverride()
        val oldCreateFromParcelFunction = parcelableClass.findCreatorClass()?.findCreateFromParcel()

        val shouldAddParcelerSupertype =
            parcelerObject == null || ParcelizeNames.PARCELER_CLASS_IDS.none { parcelerObject.hasSuperClass(it) }

        val parcelerSupertypeEntriesToRemove = parcelerOrCompanion?.superTypeListEntries?.mapNotNull {
            if (it.typeReference?.hasSuperClass(ParcelizeNames.CREATOR_ID) == true) {
                it.createSmartPointer()
            } else {
                null
            }
        } ?: emptyList()

        val parcelerCreatorPropertiesToRemove =
            parcelerOrCompanion?.declarations?.asSequence()
                ?.filterIsInstance<KtProperty>()
                ?.filter {
                    it.name == ParcelizeNames.CREATOR_NAME.identifier && it.isJvmField
                }
                ?.map { it.createSmartPointer() }
                ?.toList() ?: emptyList()

        val describeContentsFunctionToRemove = parcelableClass.findDescribeContentsOverride()?.takeIf {
            val returnExpr = it.bodyExpression?.getSingleUnwrappedStatementOrThis()
            return@takeIf (
                    returnExpr is KtReturnExpression
                            && returnExpr.getTargetLabel() == null
                            // Only remove describeContents() functions that return 0 with no further overrides.
                            && returnExpr.returnedExpression?.evaluateAsConstantInt() == 0
                            && it.overrideCount == 1)
        }

        return PreparedAction(
            parcelerObject = parcelerObject?.createSmartPointer(),
            parcelerWriteFunction = parcelerObject?.findWriteImplementation()?.createSmartPointer(),
            parcelerCreateFunction = parcelerObject?.findCreateImplementation()?.createSmartPointer(),
            parcelerNewArrayFunction = parcelerOrCompanion?.findNewArrayOverride()?.createSmartPointer(),

            writeToParcelFunction = oldWriteToParcelFunction?.createSmartPointer(),
            createFromParcelFunction = oldCreateFromParcelFunction?.createSmartPointer(),

            shouldAddParcelerSupertype = shouldAddParcelerSupertype,
            parcelerSupertypesToRemove = parcelerSupertypeEntriesToRemove,
            parcelerCreatorPropertiesToRemove = parcelerCreatorPropertiesToRemove,
            describeContentsFunctionToRemove = describeContentsFunctionToRemove?.createSmartPointer()
        )
    }

    data class PreparedAction(
        private val parcelerObject: SmartPsiElementPointer<KtObjectDeclaration>?,
        private val parcelerWriteFunction: SmartPsiElementPointer<KtNamedFunction>?,
        private val parcelerCreateFunction: SmartPsiElementPointer<KtNamedFunction>?,
        private val parcelerNewArrayFunction: SmartPsiElementPointer<KtNamedFunction>?,

        private val writeToParcelFunction: SmartPsiElementPointer<KtNamedFunction>?,
        private val createFromParcelFunction: SmartPsiElementPointer<KtNamedFunction>?,

        private val shouldAddParcelerSupertype: Boolean,
        private val parcelerSupertypesToRemove: List<SmartPsiElementPointer<KtSuperTypeListEntry>>,
        private val parcelerCreatorPropertiesToRemove: List<SmartPsiElementPointer<KtProperty>>,
        private val describeContentsFunctionToRemove: SmartPsiElementPointer<KtNamedFunction>?,
    ) {

        fun execute(parcelableClass: KtClass, ktPsiFactory: KtPsiFactory) {
            val parcelerObject = this.parcelerObject?.element ?: parcelableClass.getOrCreateCompanionObject()

            val parcelerTypeArg = parcelableClass.name ?: run {
                LOG.error("Parceler class should not be an anonymous class")
                return
            }

            if (shouldAddParcelerSupertype) {
                val entryText = "${ParcelizeNames.PARCELER_FQN.asString()}<$parcelerTypeArg>"
                parcelerObject.addSuperTypeListEntry(ktPsiFactory.createSuperTypeEntry(entryText)).shortenReferences()
            }

            parcelerSupertypesToRemove.mapNotNull { it.element }.forEach { parcelerObject.removeSuperTypeListEntry(it) }

            if (parcelerObject.name == ParcelizeNames.CREATOR_NAME.identifier) {
                parcelerObject.nameIdentifier?.delete()
            }

            if (writeToParcelFunction != null) {
                parcelerWriteFunction?.element?.delete() // Remove old implementation

                val oldFunction = writeToParcelFunction.element ?: return
                val newFunction = oldFunction.copy() as KtFunction
                oldFunction.delete()

                newFunction.setName(PARCELER_WRITE_FUNCTION_NAME.asString())
                newFunction.setModifierList(ktPsiFactory.createModifierList(KtTokens.OVERRIDE_KEYWORD))
                newFunction.setReceiverTypeReference(ktPsiFactory.createType(parcelerTypeArg))
                newFunction.valueParameterList?.apply {
                    assert(parameters.size == 2)
                    val parcelParameterName = parameters[0].name ?: ParcelizeNames.DEST_NAME.identifier
                    val flagsParameterName = parameters[1].name ?: ParcelizeNames.FLAGS_NAME.identifier

                    repeat(parameters.size) { removeParameter(0) }
                    addParameter(ktPsiFactory.createParameter("$parcelParameterName : ${ParcelizeNames.PARCEL_ID.asFqNameString()}"))
                    addParameter(ktPsiFactory.createParameter("$flagsParameterName : Int"))
                }

                parcelerObject.addDeclaration(newFunction).valueParameterList?.shortenReferences()
            } else if (parcelerWriteFunction == null) {
                val writeFunction =
                    "fun $parcelerTypeArg.write(" +
                        "${ParcelizeNames.DEST_NAME.identifier}: ${ParcelizeNames.PARCEL_ID.asFqNameString()}, " +
                        "${ParcelizeNames.FLAGS_NAME.identifier}: Int) = TODO()"
                parcelerObject.addDeclaration(ktPsiFactory.createFunction(writeFunction)).valueParameterList?.shortenReferences()
            }

            if (createFromParcelFunction != null) {
                parcelerCreateFunction?.element?.delete() // Remove old implementation

                val oldFunction = createFromParcelFunction.element ?: return
                val newFunction = oldFunction.copy() as KtFunction
                if (oldFunction.containingClassOrObject == parcelerObject) {
                    oldFunction.delete()
                }

                newFunction.setName(PARCELER_CREATE_FUNCTION_NAME.asString())
                newFunction.setModifierList(ktPsiFactory.createModifierList(KtTokens.OVERRIDE_KEYWORD))
                newFunction.setReceiverTypeReference(null)
                newFunction.valueParameterList?.apply {
                    assert(parameters.size == 1)
                    val parcelParameterName = parameters[0].name ?: "parcel"

                    removeParameter(0)
                    addParameter(ktPsiFactory.createParameter("$parcelParameterName : ${ParcelizeNames.PARCEL_ID.asFqNameString()}"))
                }

                parcelerObject.addDeclaration(newFunction).valueParameterList?.shortenReferences()
            } else if (parcelerCreateFunction == null) {
                val createFunction = "override fun create(parcel: ${ParcelizeNames.PARCEL_ID.asFqNameString()}): $parcelerTypeArg = TODO()"
                parcelerObject.addDeclaration(ktPsiFactory.createFunction(createFunction)).valueParameterList?.shortenReferences()
            }

            // Always use the default newArray() implementation
            parcelerNewArrayFunction?.element?.delete()

            // Remove describeContents() if it's the default implementation.
            describeContentsFunctionToRemove?.element?.delete()

            parcelerCreatorPropertiesToRemove.forEach { it.element?.delete() }
        }
    }
}

interface ParcelMigrateToParcelizeResolver<CONTEXT> {
    context(_: CONTEXT)
    val KtCallableDeclaration.returnTypeClassId: ClassId?

    context(_: CONTEXT)
    val KtCallableDeclaration.receiverTypeClassId: ClassId?

    context(_: CONTEXT)
    val KtCallableDeclaration.overrideCount: Int

    context(_: CONTEXT)
    val KtProperty.isJvmField: Boolean

    context(_: CONTEXT)
    fun KtClassOrObject.hasSuperClass(superTypeClassId: ClassId): Boolean

    context(_: CONTEXT)
    fun KtTypeReference.hasSuperClass(superTypeClassId: ClassId): Boolean

    context(_: CONTEXT)
    fun KtCallExpression.resolveToConstructedClass(): KtClassOrObject?

    context(_: CONTEXT)
    fun KtExpression.evaluateAsConstantInt(): Int?
}

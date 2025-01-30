// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.expectActual

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.idea.base.psi.isInlineOrValue
import org.jetbrains.kotlin.idea.base.util.names.FqNames
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.codeInsight.shorten.addToBeShortenedDescendantsToWaitingSet
import org.jetbrains.kotlin.idea.core.overrideImplement.*
import org.jetbrains.kotlin.idea.core.toDescriptor
import org.jetbrains.kotlin.idea.quickfix.TypeAccessibilityChecker
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.isEffectivelyActual
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.multiplatform.OptionalAnnotationUtil
import org.jetbrains.kotlin.resolve.source.KotlinSourceElement
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object ExpectActualGenerationUtils {
    /**
     * Returns an 'actual' declaration for the corresponding [expectDeclaration] in the context of the provided [module]
     */
    fun generateActualDeclaration(project: Project, module: Module, expectDeclaration: KtNamedDeclaration): KtDeclaration {
        val checker = TypeAccessibilityChecker.create(project, module)

        return when (expectDeclaration) {
            is KtCallableDeclaration -> {
                val descriptor = expectDeclaration.toDescriptor() as? CallableMemberDescriptor
                    ?: error("Not a callable member: $expectDeclaration")
                generateCallable(
                    project = project,
                    generateExpect = false,
                    originalDeclaration = expectDeclaration,
                    descriptor = descriptor,
                    generatedClass = null,
                    checker = checker
                )
            }

            is KtClassOrObject -> generateClassOrObject(
                project = project,
                psiFactory = KtPsiFactory(project),
                generateExpectClass = false,
                originalClass = expectDeclaration,
                checker = checker
            )

            else -> error("Unsupported declaration for actual keyword: $expectDeclaration")
        }
    }

    @ApiStatus.Internal
    fun generateCallable(
        project: Project,
        generateExpect: Boolean,
        originalDeclaration: KtDeclaration,
        descriptor: CallableMemberDescriptor,
        generatedClass: KtClassOrObject? = null,
        checker: TypeAccessibilityChecker
    ): KtCallableDeclaration {
        descriptor.checkAccessibility(checker)
        val memberChooserObject = OverrideMemberChooserObject.create(
            originalDeclaration, descriptor, descriptor,
            if (generateExpect || descriptor.modality == Modality.ABSTRACT) BodyType.NoBody else BodyType.EmptyOrTemplate
        )
        return memberChooserObject.generateMember(
            targetClass = generatedClass,
            copyDoc = true,
            project = project,
            mode = if (generateExpect) MemberGenerateMode.EXPECT else MemberGenerateMode.ACTUAL
        ).apply {
            repair(this, generatedClass, descriptor, checker)
        }
    }

    @ApiStatus.Internal
    fun generateClassOrObject(
        project: Project,
        psiFactory: KtPsiFactory,
        generateExpectClass: Boolean,
        originalClass: KtClassOrObject,
        checker: TypeAccessibilityChecker
    ): KtClassOrObject {
        val generatedClass = psiFactory.createClassHeaderCopyByText(originalClass)
        val context = originalClass.analyzeWithContent()
        val superNames = repairSuperTypeList(
            psiFactory,
            generatedClass,
            originalClass,
            generateExpectClass,
            checker,
            context
        )

        generatedClass.annotationEntries.zip(originalClass.annotationEntries).forEach { (generatedEntry, originalEntry) ->
            val annotationDescriptor = context.get(BindingContext.ANNOTATION, originalEntry)
            if (annotationDescriptor != null && !isValidInModule(annotationDescriptor, checker)) {
                generatedEntry.delete()
            }
        }

        if (generateExpectClass) {
            if (originalClass.isTopLevel()) {
                generatedClass.addModifier(KtTokens.EXPECT_KEYWORD)
            } else {
                generatedClass.makeNotActual()
            }
            generatedClass.removeModifier(KtTokens.DATA_KEYWORD)
        } else {
            if (generatedClass !is KtEnumEntry) {
                generatedClass.addModifier(KtTokens.ACTUAL_KEYWORD)
            }
        }

        val existingFqNamesWithSuperTypes = (checker.existingTypeNames + superNames).toSet()
        declLoop@ for (originalDeclaration in originalClass.declarations) {
            val descriptor = originalDeclaration.toDescriptor() ?: continue
            if (generateExpectClass && !originalDeclaration.isEffectivelyActual(false)) continue
            val generatedDeclaration: KtDeclaration = when (originalDeclaration) {
                is KtClassOrObject -> generateClassOrObject(
                    project,
                    psiFactory,
                    generateExpectClass,
                    originalDeclaration,
                    checker
                )

                is KtFunction, is KtProperty -> checker.runInContext(existingFqNamesWithSuperTypes) {
                    generateCallable(
                        project,
                        generateExpectClass,
                        originalDeclaration,
                        descriptor as CallableMemberDescriptor,
                        generatedClass,
                        this
                    )
                }

                else -> continue@declLoop
            }
            generatedClass.addDeclaration(generatedDeclaration)
        }
        if (!originalClass.isAnnotation() && originalClass.safeAs<KtClass>()?.isInlineOrValue() == false) {
            for (originalProperty in originalClass.primaryConstructorParameters) {
                if (!originalProperty.hasValOrVar() || !originalProperty.hasActualModifier()) continue
                val descriptor = originalProperty.toDescriptor() as? PropertyDescriptor ?: continue
                checker.runInContext(existingFqNamesWithSuperTypes) {
                    val generatedProperty = generateCallable(
                        project,
                        generateExpectClass,
                        originalProperty,
                        descriptor,
                        generatedClass,
                        this
                    )
                    generatedClass.addDeclaration(generatedProperty)
                }
            }
        }
        val originalPrimaryConstructor = originalClass.primaryConstructor
        if (
            generatedClass is KtClass
            && originalPrimaryConstructor != null
            && (!generateExpectClass || originalPrimaryConstructor.hasActualModifier())
        ) {
            val descriptor = originalPrimaryConstructor.toDescriptor()
            if (descriptor is FunctionDescriptor) {
                checker.runInContext(existingFqNamesWithSuperTypes) {
                    val expectedPrimaryConstructor = generateCallable(
                        project,
                        generateExpectClass,
                        originalPrimaryConstructor,
                        descriptor,
                        generatedClass,
                        this
                    )
                    generatedClass.createPrimaryConstructorIfAbsent().replace(expectedPrimaryConstructor)
                }
            }
        }

        return generatedClass
    }

    private fun KtPsiFactory.createClassHeaderCopyByText(originalClass: KtClassOrObject): KtClassOrObject {
        val text = originalClass.text
        return when (originalClass) {
            is KtObjectDeclaration -> if (originalClass.isCompanion()) {
                createCompanionObject(text)
            } else {
                createObject(text)
            }

            is KtEnumEntry -> createEnumEntry(text)
            else -> createClass(text)
        }.apply {
            declarations.forEach(KtDeclaration::delete)
            primaryConstructor?.delete()
        }
    }

    private fun CallableMemberDescriptor.checkAccessibility(checker: TypeAccessibilityChecker) {
        val errors = checker.incorrectTypes(this).ifEmpty { return }
        throw KotlinTypeInaccessibleException(errors.toSet())
    }

    private fun isValidInModule(annotationDescriptor: AnnotationDescriptor, checker: TypeAccessibilityChecker): Boolean {
        return annotationDescriptor.fqName !in forbiddenAnnotationFqNames && checker.checkAccessibility(annotationDescriptor.type)
    }

    private fun repair(
        declaration: KtCallableDeclaration,
        generatedClass: KtClassOrObject?,
        descriptor: CallableDescriptor,
        checker: TypeAccessibilityChecker
    ) {
        if (generatedClass != null) repairOverride(declaration, descriptor, checker)
        repairAnnotationEntries(declaration, descriptor, checker)
    }

    private fun repairSuperTypeList(
        psiFactory: KtPsiFactory,
        generated: KtClassOrObject,
        original: KtClassOrObject,
        generateExpectClass: Boolean,
        checker: TypeAccessibilityChecker,
        context: BindingContext
    ): Collection<String> {
        val superNames = linkedSetOf<String>()
        val typeParametersFqName = context[BindingContext.DECLARATION_TO_DESCRIPTOR, original]
            ?.safeAs<ClassDescriptor>()
            ?.declaredTypeParameters?.mapNotNull { it.fqNameOrNull()?.asString() }.orEmpty()

        checker.runInContext(checker.existingTypeNames + typeParametersFqName) {
            generated.superTypeListEntries.zip(original.superTypeListEntries).forEach { (generatedEntry, originalEntry) ->
                val superType = context[BindingContext.TYPE, originalEntry.typeReference]
                val superClassDescriptor = superType?.constructor?.declarationDescriptor as? ClassDescriptor ?: return@forEach
                if (generateExpectClass && !checker.checkAccessibility(superType)) {
                    generatedEntry.delete()
                    return@forEach
                }

                superType.fqName?.shortName()?.asString()?.let { superNames += it }
                if (generateExpectClass) {
                    if (generatedEntry !is KtSuperTypeCallEntry) return@forEach
                } else {
                    if (generatedEntry !is KtSuperTypeEntry) return@forEach
                }

                if (superClassDescriptor.kind == ClassKind.CLASS || superClassDescriptor.kind == ClassKind.ENUM_CLASS) {
                    val entryText = IdeDescriptorRenderers.SOURCE_CODE.renderType(superType)
                    val newGeneratedEntry = if (generateExpectClass) {
                        psiFactory.createSuperTypeEntry(entryText)
                    } else {
                        psiFactory.createSuperTypeCallEntry("$entryText()")
                    }
                    generatedEntry.replace(newGeneratedEntry).safeAs<KtElement>()?.addToBeShortenedDescendantsToWaitingSet()
                }
            }
        }
        if (generated.superTypeListEntries.isEmpty()) generated.getSuperTypeList()?.delete()
        return superNames
    }

    private fun repairOverride(declaration: KtCallableDeclaration, descriptor: CallableDescriptor, checker: TypeAccessibilityChecker) {
        if (!declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return

        val superDescriptor = descriptor.overriddenDescriptors.firstOrNull()?.containingDeclaration
        if (superDescriptor?.fqNameOrNull()?.shortName()?.asString() !in checker.existingTypeNames) {
            declaration.removeModifier(KtTokens.OVERRIDE_KEYWORD)
        }
    }

    private fun repairAnnotations(checker: TypeAccessibilityChecker, target: KtModifierListOwner, annotations: Annotations) {
        for (annotation in annotations) {
            if (isValidInModule(annotation, checker)) {
                checkAndAdd(annotation, checker, target)
            }
        }
    }

    private fun repairAnnotationEntries(
        typeReference: KtTypeReference,
        type: KotlinType,
        checker: TypeAccessibilityChecker
    ) {
        repairAnnotations(checker, typeReference, type.annotations)
        typeReference.typeElement?.typeArgumentsAsTypes?.zip(type.arguments)?.forEach { (reference, projection) ->
            repairAnnotationEntries(reference, projection.type, checker)
        }
    }

    private fun repairAnnotationEntries(
        target: KtModifierListOwner,
        descriptor: DeclarationDescriptorNonRoot,
        checker: TypeAccessibilityChecker
    ) {
        repairAnnotations(checker, target, descriptor.annotations)
        when (descriptor) {
            is ValueParameterDescriptor -> {
                if (target !is KtParameter) return
                val typeReference = target.typeReference ?: return
                repairAnnotationEntries(typeReference, descriptor.type, checker)
            }

            is TypeParameterDescriptor -> {
                if (target !is KtTypeParameter) return
                val extendsBound = target.extendsBound ?: return
                for (upperBound in descriptor.upperBounds) {
                    repairAnnotationEntries(extendsBound, upperBound, checker)
                }
            }

            is CallableDescriptor -> {
                val extension = descriptor.extensionReceiverParameter
                val receiver = target.safeAs<KtCallableDeclaration>()?.receiverTypeReference
                if (extension != null && receiver != null) {
                    repairAnnotationEntries(receiver, extension, checker)
                }

                val callableDeclaration = target.safeAs<KtCallableDeclaration>() ?: return
                callableDeclaration.typeParameters.zip(descriptor.typeParameters).forEach { (typeParameter, typeParameterDescriptor) ->
                    repairAnnotationEntries(typeParameter, typeParameterDescriptor, checker)
                }

                callableDeclaration.valueParameters.zip(descriptor.valueParameters).forEach { (valueParameter, valueParameterDescriptor) ->
                    repairAnnotationEntries(valueParameter, valueParameterDescriptor, checker)
                }
            }
        }
    }

    private fun checkAndAdd(annotationDescriptor: AnnotationDescriptor, checker: TypeAccessibilityChecker, target: KtModifierListOwner) {
        if (isValidInModule(annotationDescriptor, checker)) {
            val entry = annotationDescriptor.source.safeAs<KotlinSourceElement>()?.psi.safeAs<KtAnnotationEntry>() ?: return
            target.addAnnotationEntry(entry)
        }
    }

    private val forbiddenAnnotationFqNames = setOf(
        OptionalAnnotationUtil.OPTIONAL_EXPECTATION_FQ_NAME,
        FqName("kotlin.ExperimentalMultiplatform"),
        OptInNames.OPT_IN_FQ_NAME,
        FqNames.OptInFqNames.OLD_USE_EXPERIMENTAL_FQ_NAME
    )
}
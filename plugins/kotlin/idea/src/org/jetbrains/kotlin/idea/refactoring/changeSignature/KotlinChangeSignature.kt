// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.changeSignature.*
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.refactoring.util.CanonicalTypes
import com.intellij.util.VisibilityUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.asJava.getRepresentativeLightMethod
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.refactoring.CallableRefactoring
import org.jetbrains.kotlin.idea.refactoring.broadcastRefactoringExit
import org.jetbrains.kotlin.idea.refactoring.changeSignature.ui.KotlinChangePropertySignatureDialog
import org.jetbrains.kotlin.idea.refactoring.changeSignature.ui.KotlinChangeSignatureDialog
import org.jetbrains.kotlin.idea.refactoring.createJavaMethod
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.application.withPsiAttachment
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

interface KotlinChangeSignatureConfiguration {
    fun configure(originalDescriptor: KotlinMethodDescriptor): KotlinMethodDescriptor = originalDescriptor
    fun performSilently(affectedFunctions: Collection<PsiElement>): Boolean = false
    fun forcePerformForSelectedFunctionOnly(): Boolean = false

    object Empty : KotlinChangeSignatureConfiguration
}

fun KotlinMethodDescriptor.modify(action: (KotlinMutableMethodDescriptor) -> Unit): KotlinMethodDescriptor {
    val newDescriptor = KotlinMutableMethodDescriptor(this)
    action(newDescriptor)
    return newDescriptor
}

fun runChangeSignature(
    project: Project,
    editor: Editor?,
    callableDescriptor: CallableDescriptor,
    configuration: KotlinChangeSignatureConfiguration,
    defaultValueContext: PsiElement,
    @NlsContexts.Command commandName: String? = null
): Boolean {
    val result = KotlinChangeSignature(project, editor, callableDescriptor, configuration, defaultValueContext, commandName).run()
    if (!result) {
        broadcastRefactoringExit(project, "refactoring.changeSignature")
    }
    return result
}

class KotlinChangeSignature(
    project: Project,
    editor: Editor?,
    callableDescriptor: CallableDescriptor,
    val configuration: KotlinChangeSignatureConfiguration,
    val defaultValueContext: PsiElement,
    @NlsContexts.Command commandName: String?
) : CallableRefactoring<CallableDescriptor>(
    project,
    editor,
    callableDescriptor,
    commandName ?: RefactoringBundle.message("changeSignature.refactoring.name")
) {
    override fun forcePerformForSelectedFunctionOnly() = configuration.forcePerformForSelectedFunctionOnly()

    /**
     * @param propertyProcessor:
     * - top level
     * - member level
     * - primary constructor
     *
     * @param functionProcessor:
     * - top level
     * - member level
     * - local level
     * - constructors
     *
     * @param javaProcessor:
     * - member level
     */
    private fun <T> selectRefactoringProcessor(
        descriptor: KotlinMethodDescriptor,
        propertyProcessor: (KotlinMethodDescriptor) -> T?,
        functionProcessor: (KotlinMethodDescriptor) -> T?,
        javaProcessor: (KotlinMethodDescriptor, PsiMethod) -> T?,
    ): T? {
        return when (val baseDeclaration = descriptor.baseDeclaration) {
            is KtProperty, is KtParameter -> propertyProcessor(descriptor)
            /**
             * functions:
             * - top level
             * - member level
             * - constructors
             *
             * class:
             * - primary constructor
             */
            is KtFunction, is KtClass -> functionProcessor(descriptor)
            is PsiMethod -> {
                if (baseDeclaration.language != JavaLanguage.INSTANCE) {
                    Messages.showErrorDialog(
                        KotlinBundle.message("error.text.can.t.change.signature.of.method", baseDeclaration.language.displayName),
                        commandName
                    )

                    return null
                }

                javaProcessor(descriptor, baseDeclaration)
            }

            else -> throw KotlinExceptionWithAttachments("Unexpected declaration: ${baseDeclaration::class}")
                .withPsiAttachment("element.kt", baseDeclaration)
                .withPsiAttachment("file.kt", baseDeclaration.containingFile)
        }
    }

    @TestOnly
    fun createSilentRefactoringProcessor(methodDescriptor: KotlinMethodDescriptor): BaseRefactoringProcessor? = selectRefactoringProcessor(
        methodDescriptor,
        propertyProcessor = { KotlinChangePropertySignatureDialog.createProcessorForSilentRefactoring(project, commandName, it) },
        functionProcessor = {
            KotlinChangeSignatureDialog.createRefactoringProcessorForSilentChangeSignature(
                project,
                commandName,
                it,
                defaultValueContext
            )
        },
        javaProcessor = { descriptor, _ -> ChangeSignatureProcessor(project, getPreviewInfoForJavaMethod(descriptor).second) }
    )

    private fun runSilentRefactoring(methodDescriptor: KotlinMethodDescriptor) {
        createSilentRefactoringProcessor(methodDescriptor)?.run()
    }

    private fun runInteractiveRefactoring(methodDescriptor: KotlinMethodDescriptor) {
        val dialog = selectRefactoringProcessor(
            methodDescriptor,
            propertyProcessor = { KotlinChangePropertySignatureDialog(project, it, commandName) },
            functionProcessor = { KotlinChangeSignatureDialog(project, editor, it, defaultValueContext, commandName) },
            javaProcessor = fun(descriptor: KotlinMethodDescriptor, method: PsiMethod): RefactoringDialog? {
                if (descriptor is KotlinChangeSignatureData) {
                    ChangeSignatureUtil.invokeChangeSignatureOn(method, project)
                    return null
                }

                val (preview, javaChangeInfo) = getPreviewInfoForJavaMethod(descriptor)
                val javaDescriptor = object : JavaMethodDescriptor(preview) {
                    @Suppress("UNCHECKED_CAST")
                    override fun getParameters() = javaChangeInfo.newParameters.toMutableList() as MutableList<ParameterInfoImpl>
                }

                return object : JavaChangeSignatureDialog(project, javaDescriptor, false, null) {
                    override fun createRefactoringProcessor(): BaseRefactoringProcessor {
                        val parameters = parameters
                        LOG.assertTrue(myMethod.method.isValid)
                        val newJavaChangeInfo = JavaChangeInfoImpl(
                            visibility ?: VisibilityUtil.getVisibilityModifier(myMethod.method.modifierList),
                            javaChangeInfo.method,
                            methodName,
                            returnType ?: CanonicalTypes.createTypeWrapper(PsiType.VOID),
                            parameters.toTypedArray(),
                            exceptions,
                            isGenerateDelegate,
                            myMethodsToPropagateParameters ?: HashSet(),
                            myMethodsToPropagateExceptions ?: HashSet()
                        ).also {
                            it.setCheckUnusedParameter()
                        }

                        return ChangeSignatureProcessor(myProject, newJavaChangeInfo)
                    }
                }
            },
        ) ?: return

        if (isUnitTestMode()) {
            try {
                dialog.performOKAction()
            } finally {
                dialog.close(DialogWrapper.OK_EXIT_CODE)
            }
        } else {
            dialog.show()
        }
    }

    private fun getPreviewInfoForJavaMethod(descriptor: KotlinMethodDescriptor): Pair<PsiMethod, JavaChangeInfo> {
        val originalMethod = descriptor.baseDeclaration as PsiMethod
        val contextFile = defaultValueContext.containingFile as KtFile

        // Generate new Java method signature from the Kotlin point of view
        val ktChangeInfo = KotlinChangeInfo(methodDescriptor = descriptor, context = defaultValueContext)
        val ktSignature = ktChangeInfo.getNewSignature(descriptor.originalPrimaryCallable)
        val previewClassName = if (originalMethod.isConstructor) originalMethod.name else "Dummy"
        val dummyFileText = with(StringBuilder()) {
            contextFile.packageDirective?.let { append(it.text).append("\n") }
            append("class $previewClassName {\n").append(ktSignature).append("{}\n}")
            toString()
        }
        val dummyFile = KtPsiFactory(project).createFileWithLightClassSupport("dummy.kt", dummyFileText, originalMethod)
        val dummyDeclaration = (dummyFile.declarations.first() as KtClass).body!!.declarations.first()

        // Convert to PsiMethod which can be used in Change Signature dialog
        val containingClass = PsiElementFactory.getInstance(project).createClass(previewClassName)
        val preview = createJavaMethod(dummyDeclaration.getRepresentativeLightMethod()!!, containingClass)

        // Create JavaChangeInfo based on new signature
        // TODO: Support visibility change
        val visibility = VisibilityUtil.getVisibilityModifier(originalMethod.modifierList)
        val returnType = CanonicalTypes.createTypeWrapper(preview.returnType ?: PsiType.VOID)
        val params = (preview.parameterList.parameters.zip(ktChangeInfo.newParameters)).map {
            val (param, paramInfo) = it
            // Keep original default value for proper update of Kotlin usages
            KotlinAwareJavaParameterInfoImpl(paramInfo.oldIndex, param.name, param.type, paramInfo.defaultValueForCall)
        }.toTypedArray()

        return preview to JavaChangeInfoImpl(
            visibility,
            originalMethod,
            preview.name,
            returnType,
            params,
            arrayOf<ThrownExceptionInfo>(),
            false,
            emptySet<PsiMethod>(),
            emptySet<PsiMethod>()
        )
    }

    override fun performRefactoring(descriptorsForChange: Collection<CallableDescriptor>) {
        val adjustedDescriptor = adjustDescriptor(descriptorsForChange) ?: return

        val affectedFunctions = adjustedDescriptor.affectedCallables.mapNotNull { it.element }
        if (affectedFunctions.any { !checkModifiable(it) }) return

        if (configuration.performSilently(affectedFunctions)) {
            runSilentRefactoring(adjustedDescriptor)
        } else {
            runInteractiveRefactoring(adjustedDescriptor)
        }
    }

    fun adjustDescriptor(descriptorsForSignatureChange: Collection<CallableDescriptor>): KotlinMethodDescriptor? {
        val baseDescriptor = preferContainedInClass(descriptorsForSignatureChange)
        val functionDeclaration = DescriptorToSourceUtilsIde.getAnyDeclaration(project, baseDescriptor)
        if (functionDeclaration == null) {
            LOG.error("Could not find declaration for $baseDescriptor")
            return null
        }

        if (!checkModifiable(functionDeclaration)) {
            return null
        }

        val originalDescriptor = KotlinChangeSignatureData(baseDescriptor, functionDeclaration, descriptorsForSignatureChange)
        return configuration.configure(originalDescriptor)
    }

    private fun preferContainedInClass(descriptorsForSignatureChange: Collection<CallableDescriptor>): CallableDescriptor {
        for (descriptor in descriptorsForSignatureChange) {
            val containingDeclaration = descriptor.containingDeclaration
            if (containingDeclaration is ClassDescriptor && containingDeclaration.kind != ClassKind.INTERFACE) {
                return descriptor
            }
        }
        //choose at random
        return descriptorsForSignatureChange.first()
    }

    companion object {
        private val LOG = logger<KotlinChangeSignature>()
    }
}

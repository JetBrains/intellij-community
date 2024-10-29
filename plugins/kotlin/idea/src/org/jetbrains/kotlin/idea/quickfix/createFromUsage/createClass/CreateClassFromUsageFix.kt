// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.quickfix.IntentionActionPriority
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.ClassKind
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateClassUtil
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateFromUsageFixBase
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.*
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.types.typeUtil.isUnit
import java.util.*

val ClassKind.actionPriority: IntentionActionPriority
    get() = if (this == ClassKind.ANNOTATION_CLASS) IntentionActionPriority.LOW else IntentionActionPriority.NORMAL

data class ClassInfo(
    val kind: ClassKind = ClassKind.DEFAULT,
    val name: String,
    private val targetParents: List<PsiElement>,
    val expectedTypeInfo: TypeInfo,
    val inner: Boolean = false,
    val open: Boolean = false,
    val typeArguments: List<TypeInfo> = Collections.emptyList(),
    val parameterInfos: List<ParameterInfo> = Collections.emptyList(),
    val primaryConstructorVisibility: DescriptorVisibility? = null
) {
    val applicableParents: List<PsiElement> by lazy {
        targetParents.filter {
            if (kind == ClassKind.OBJECT && it is KtClass && (it.isInner() || it.isLocal)) return@filter false
            true
        }
    }
}

open class CreateClassFromUsageFix<E : KtElement> protected constructor(
    element: E,
    private val classInfo: ClassInfo
) : CreateFromUsageFixBase<E>(element) {
    override fun getText(): String = KotlinBundle.message("create.0.1", classInfo.kind.description, classInfo.name)

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        if (classInfo.kind == ClassKind.DEFAULT ||
            classInfo.applicableParents.isEmpty()) return false
        classInfo.applicableParents.forEach {
            if (it is PsiClass) {
                if (classInfo.kind == ClassKind.OBJECT ||
                    classInfo.kind == ClassKind.ENUM_ENTRY ||
                    it.isInterface && classInfo.inner) return false
            }
        }
        return true
    }

    override fun startInWriteAction(): Boolean = false

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        if (editor == null) return

        CreateClassUtil.chooseAndCreateClass(project, editor, file, element, classInfo.kind, classInfo.applicableParents, classInfo.name, text) { targetParent ->
            runCreateClassBuilder(file, editor, targetParent, classInfo.name)
        }
    }

    private fun runCreateClassBuilder(
        file: KtFile,
        editor: Editor,
        targetParent: PsiElement,
        className: String
    ) {
        val element = element ?: return

        val constructorInfo = ClassWithPrimaryConstructorInfo(
            classInfo,
            // Need for #KT-22137
            if (classInfo.expectedTypeInfo.isUnit) TypeInfo.Empty else classInfo.expectedTypeInfo,
            primaryConstructorVisibility = classInfo.primaryConstructorVisibility
        )
        val builder = CallableBuilderConfiguration(
            Collections.singletonList(constructorInfo),
            element,
            file,
            editor,
            false,
            classInfo.kind == ClassKind.PLAIN_CLASS || classInfo.kind == ClassKind.INTERFACE
        ).createBuilder()
        builder.placement = CallablePlacement.NoReceiver(targetParent)

        file.project.executeCommand(text, command = {
            builder.build {
                if (targetParent !is KtFile || targetParent == file) return@build
                val targetPackageFqName = targetParent.packageFqName
                if (targetPackageFqName == file.packageFqName) return@build
                val reference = (element.getQualifiedElementSelector() as? KtSimpleNameExpression)?.mainReference ?: return@build
                reference.bindToFqName(
                    targetPackageFqName.child(Name.identifier(className)),
                    KtSimpleNameReference.ShorteningMode.FORCED_SHORTENING
                )
            }
        })
    }

    private class LowPriorityCreateClassFromUsageFix<E : KtElement>(
        element: E,
        classInfo: ClassInfo
    ) : CreateClassFromUsageFix<E>(element, classInfo), LowPriorityAction

    private class HighPriorityCreateClassFromUsageFix<E : KtElement>(
        element: E,
        classInfo: ClassInfo
    ) : CreateClassFromUsageFix<E>(element, classInfo), HighPriorityAction

    companion object {
        fun <E : KtElement> create(element: E, classInfo: ClassInfo): CreateClassFromUsageFix<E> {
            return when (classInfo.kind.actionPriority) {
                IntentionActionPriority.NORMAL -> CreateClassFromUsageFix(element, classInfo)
                IntentionActionPriority.LOW -> LowPriorityCreateClassFromUsageFix(element, classInfo)
                IntentionActionPriority.HIGH -> HighPriorityCreateClassFromUsageFix(element, classInfo)
            }
        }
    }
}

private val TypeInfo.isUnit: Boolean
    get() = ((this as? TypeInfo.DelegatingTypeInfo)?.delegate as? TypeInfo.ByType)?.theType?.isUnit() == true

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.generation.OverrideImplementUtil
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.java.JavaBundle
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ui.popup.IPopupChooserBuilder
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.components.JBList
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaClassDescriptor
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.ClassListCellRenderer
import org.jetbrains.kotlin.idea.core.overrideImplement.BodyType
import org.jetbrains.kotlin.idea.core.overrideImplement.GenerateMembersHandler
import org.jetbrains.kotlin.idea.core.overrideImplement.OverrideMemberChooserObject
import org.jetbrains.kotlin.idea.core.util.runSynchronouslyWithProgress
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.search.declarationsSearch.HierarchySearchRequest
import org.jetbrains.kotlin.idea.search.declarationsSearch.searchInheritors
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.getTypeSubstitution
import org.jetbrains.kotlin.idea.util.substitute
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.util.findCallableMemberBySignature
import javax.swing.ListSelectionModel

abstract class ImplementAbstractMemberIntentionBase : SelfTargetingRangeIntention<KtNamedDeclaration>(
    KtNamedDeclaration::class.java,
    KotlinBundle.lazyMessage("implement.abstract.member")
) {
    companion object {
        private val LOG = Logger.getInstance("#${ImplementAbstractMemberIntentionBase::class.java.canonicalName}")
    }

    protected fun findExistingImplementation(
        subClass: ClassDescriptor,
        superMember: CallableMemberDescriptor
    ): CallableMemberDescriptor? {
        val superClass = superMember.containingDeclaration as? ClassDescriptor ?: return null
        val substitution = getTypeSubstitution(superClass.defaultType, subClass.defaultType).orEmpty()
        val signatureInSubClass = superMember.substitute(substitution) as? CallableMemberDescriptor ?: return null
        val subMember = subClass.findCallableMemberBySignature(signatureInSubClass)
        return if (subMember?.kind?.isReal == true) subMember else null
    }

    protected abstract fun acceptSubClass(subClassDescriptor: ClassDescriptor, memberDescriptor: CallableMemberDescriptor): Boolean

    private fun findClassesToProcess(member: KtNamedDeclaration): Sequence<PsiElement> {
        val baseClass = member.containingClassOrObject as? KtClass ?: return emptySequence()
        val memberDescriptor = member.resolveToDescriptorIfAny() as? CallableMemberDescriptor ?: return emptySequence()

        fun acceptSubClass(subClass: PsiElement): Boolean {
            if (!BaseIntentionAction.canModify(subClass)) return false
            val classDescriptor = when (subClass) {
                is KtLightClass -> subClass.kotlinOrigin?.resolveToDescriptorIfAny()
                is KtEnumEntry -> subClass.resolveToDescriptorIfAny()
                is PsiClass -> subClass.getJavaClassDescriptor()
                else -> null
            } ?: return false
            return acceptSubClass(classDescriptor, memberDescriptor)
        }

        if (baseClass.isEnum()) {
            return baseClass.declarations
                .asSequence()
                .filterIsInstance<KtEnumEntry>()
                .filter(::acceptSubClass)
        }

        return HierarchySearchRequest(baseClass, baseClass.useScope, false)
            .searchInheritors()
            .asSequence()
            .filter(::acceptSubClass)
    }

    protected abstract fun computeText(element: KtNamedDeclaration): (() -> String)?

    override fun applicabilityRange(element: KtNamedDeclaration): TextRange? {
        if (!element.isAbstract()) return null

        setTextGetter(computeText(element) ?: return null)

        if (!findClassesToProcess(element).any()) return null

        return element.nameIdentifier?.textRange
    }

    protected abstract val preferConstructorParameters: Boolean

    private fun implementInKotlinClass(editor: Editor?, member: KtNamedDeclaration, targetClass: KtClassOrObject) {
        val subClassDescriptor = targetClass.resolveToDescriptorIfAny() ?: return
        val superMemberDescriptor = member.resolveToDescriptorIfAny() as? CallableMemberDescriptor ?: return
        val superClassDescriptor = superMemberDescriptor.containingDeclaration as? ClassDescriptor ?: return
        val substitution = getTypeSubstitution(superClassDescriptor.defaultType, subClassDescriptor.defaultType).orEmpty()
        val descriptorToImplement = superMemberDescriptor.substitute(substitution) as CallableMemberDescriptor
        val chooserObject = OverrideMemberChooserObject.create(
          member.project,
          descriptorToImplement,
          descriptorToImplement,
          BodyType.FromTemplate,
          preferConstructorParameters
        )
        GenerateMembersHandler.generateMembers(editor, targetClass, listOf(chooserObject), false)
    }

    private fun implementInJavaClass(member: KtNamedDeclaration, targetClass: PsiClass) {
        member.toLightMethods().forEach { OverrideImplementUtil.overrideOrImplement(targetClass, it) }
    }

    private fun implementInClass(member: KtNamedDeclaration, targetClasses: Collection<PsiElement>) {
        val project = member.project
        project.executeCommand(JavaBundle.message("intention.implement.abstract.method.command.name")) {
            if (!FileModificationService.getInstance().preparePsiElementsForWrite(targetClasses)) return@executeCommand
            runWriteAction {
                for (targetClass in targetClasses) {
                    try {
                        val descriptor = OpenFileDescriptor(project, targetClass.containingFile.virtualFile)
                        val targetEditor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true)!!
                        when (targetClass) {
                            is KtLightClass -> targetClass.kotlinOrigin?.let { implementInKotlinClass(targetEditor, member, it) }
                            is KtEnumEntry -> implementInKotlinClass(targetEditor, member, targetClass)
                            is PsiClass -> implementInJavaClass(member, targetClass)
                        }
                    } catch (e: IncorrectOperationException) {
                        LOG.error(e)
                    }
                }
            }
        }
    }

    override fun startInWriteAction(): Boolean = false

    override fun checkFile(file: PsiFile): Boolean {
        return true
    }

    override fun preparePsiElementForWriteIfNeeded(target: KtNamedDeclaration): Boolean {
        return true
    }

    override fun applyTo(element: KtNamedDeclaration, editor: Editor?) {
        if (editor == null) throw IllegalArgumentException("This intention requires an editor")
        val project = element.project

        val classesToProcess = project.runSynchronouslyWithProgress(
            JavaBundle.message("intention.implement.abstract.method.searching.for.descendants.progress"),
            true
        ) { runReadAction { findClassesToProcess(element).toList() } } ?: return
        if (classesToProcess.isEmpty()) return

        classesToProcess.singleOrNull()?.let { return implementInClass(element, listOf(it)) }

        val renderer = ClassListCellRenderer()
        val sortedClasses = classesToProcess.sortedWith(renderer.comparator)
        if (isUnitTestMode()) return implementInClass(element, sortedClasses)

        val list = JBList(sortedClasses).apply {
            selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
            cellRenderer = renderer
        }
        val builder = PopupChooserBuilder(list)
        renderer.installSpeedSearch(builder as IPopupChooserBuilder<*>)
        builder
            .setTitle(CodeInsightBundle.message("intention.implement.abstract.method.class.chooser.title"))
            .setItemsChosenCallback {
                implementInClass(element, it)
            }
            .createPopup()
            .showInBestPositionFor(editor)
    }
}

class ImplementAbstractMemberIntention : ImplementAbstractMemberIntentionBase() {
    override fun computeText(element: KtNamedDeclaration): (() -> String)? = when (element) {
        is KtProperty -> KotlinBundle.lazyMessage("implement.abstract.property")
        is KtNamedFunction -> KotlinBundle.lazyMessage("implement.abstract.function")
        else -> null
    }

    override fun acceptSubClass(subClassDescriptor: ClassDescriptor, memberDescriptor: CallableMemberDescriptor): Boolean {
        return subClassDescriptor.kind != ClassKind.INTERFACE && findExistingImplementation(subClassDescriptor, memberDescriptor) == null
    }

    override val preferConstructorParameters: Boolean
        get() = false
}

class ImplementAbstractMemberAsConstructorParameterIntention : ImplementAbstractMemberIntentionBase() {
    override fun computeText(element: KtNamedDeclaration): (() -> String)? {
        if (element !is KtProperty) return null
        return KotlinBundle.lazyMessage("implement.as.constructor.parameter")
    }

    override fun acceptSubClass(subClassDescriptor: ClassDescriptor, memberDescriptor: CallableMemberDescriptor): Boolean {
        val kind = subClassDescriptor.kind
        return (kind == ClassKind.CLASS || kind == ClassKind.ENUM_CLASS)
                && subClassDescriptor !is JavaClassDescriptor
                && findExistingImplementation(subClassDescriptor, memberDescriptor) == null
    }

    override val preferConstructorParameters: Boolean
        get() = true

    override fun applicabilityRange(element: KtNamedDeclaration): TextRange? {
        if (element !is KtProperty) return null
        return super.applicabilityRange(element)
    }
}
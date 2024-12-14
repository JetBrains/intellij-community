// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.SmartList
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.getReturnTypeReference
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.isFunInterface
import org.jetbrains.kotlin.idea.core.TemplateKind
import org.jetbrains.kotlin.idea.core.getFunctionBodyTextFromTemplate
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.core.unblockDocument
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.actualsForExpect
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.liftToExpect
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.withExpectedActuals
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.psi.typeRefHelpers.setReceiverTypeReference
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.utils.addIfNotNull

private val LOG = Logger.getInstance(ConvertMemberToExtensionIntention::class.java)

class ConvertMemberToExtensionIntention : SelfTargetingRangeIntention<KtCallableDeclaration>(
    KtCallableDeclaration::class.java, KotlinBundle.lazyMessage("convert.member.to.extension")
), LowPriorityAction {
    private fun isApplicable(element: KtCallableDeclaration): Boolean {
        val classBody = element.parent as? KtClassBody ?: return false
        val parentClass = classBody.parent as? KtClassOrObject ?: return false
        if (parentClass.isLocal) return false
        if (parentClass is KtObjectDeclaration && parentClass.isCompanion()) return false
        if ((parentClass as? KtClass)?.isFunInterface() == true && !element.hasBody()) return false
        if (element.receiverTypeReference != null) return false
        if (element.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return false
        when (element) {
            is KtProperty -> if (element.hasInitializer() || element.hasDelegate()) return false
            is KtSecondaryConstructor -> return false
        }

        return true
    }

    override fun applicabilityRange(element: KtCallableDeclaration): TextRange? {
        val nameIdentifier = element.nameIdentifier ?: return null
        if (!isApplicable(element)) return null
        return nameIdentifier.textRange
    }

    override fun startInWriteAction() = false

    override fun applyTo(element: KtCallableDeclaration, editor: Editor?) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return

        var allowExpected = true

        liftToExpect(element)?.actualsForExpect()?.let {
            if (it.isEmpty()) {
                allowExpected = askIfExpectedIsAllowed()
            }
        }

        val (extension, bodyTypeToSelect) = createExtensionCallableAndPrepareBodyToSelect(element, allowExpected)

        runWriteAction {
            editor?.apply {
                unblockDocument()

                if (extension.isValid) {

                    if (bodyTypeToSelect != GeneratedBodyType.NOTHING) {
                        val bodyToSelect = getBodyForSelection(extension, bodyTypeToSelect)

                        if (bodyToSelect != null) {
                            val range = bodyToSelect.textRange
                            moveCaret(range.startOffset, ScrollType.CENTER)

                            val parent = bodyToSelect.parent
                            val lastSibling = if (parent is KtBlockExpression) parent.rBrace?.siblings(forward = false, withItself = false)
                                ?.first { it !is PsiWhiteSpace }
                            else bodyToSelect.siblings(forward = true, withItself = false).lastOrNull()
                            val endOffset = lastSibling?.endOffset ?: range.endOffset
                            selectionModel.setSelection(range.startOffset, endOffset)
                        } else {
                            LOG.error(
                                "Extension created with new method body but this body was not found after document commit. Extension text: \"${extension.text}\""
                            )
                            moveCaret(extension.textOffset, ScrollType.CENTER)
                        }
                    } else {
                        moveCaret(extension.textOffset, ScrollType.CENTER)
                    }
                } else {
                    LOG.error("Extension invalidated during document commit. Extension text \"${extension.text}\"")
                }
            }
        }
    }

    private fun getBodyForSelection(extension: KtCallableDeclaration, bodyTypeToSelect: GeneratedBodyType): KtExpression? {
        fun selectBody(declaration: KtDeclarationWithBody): KtExpression? {

            if (!declaration.hasBody()) return extension

            return declaration.bodyExpression?.let {
                (it as? KtBlockExpression)?.statements?.singleOrNull() ?: it
            }
        }

        return when (bodyTypeToSelect) {
            GeneratedBodyType.FUNCTION -> (extension as? KtFunction)?.let { selectBody(it) }
            GeneratedBodyType.GETTER -> (extension as? KtProperty)?.getter?.let { selectBody(it) }
            GeneratedBodyType.SETTER -> (extension as? KtProperty)?.setter?.let { selectBody(it) }
            else -> null
        }
    }

    private enum class GeneratedBodyType {
        NOTHING, FUNCTION, SETTER, GETTER
    }

    private fun processSingleDeclaration(
        element: KtCallableDeclaration, allowExpected: Boolean
    ): Pair<KtCallableDeclaration, GeneratedBodyType> {
        val containingClass = element.containingClassOrObject

        val isEffectivelyExpected = allowExpected && element.isExpectDeclaration()

        val file = element.containingKtFile
        val project = file.project
        val outermostParent = KtPsiUtil.getOutermostParent(element, file, false)

        val ktFilesToAddImports = LinkedHashSet<KtFile>()
        val javaCallsToFix = SmartList<PsiMethodCallExpression>()
        runWithModalProgressBlocking(project, KotlinBundle.message("searching.for.0", element.name!!)) {
            readAction {
                for (ref in ReferencesSearch.search(element)) {
                    when (ref) {
                        is KtReference -> {
                            val refFile = ref.element.containingKtFile
                            if (refFile != file) {
                                ktFilesToAddImports.add(refFile)
                            }
                        }

                        is PsiReferenceExpression -> javaCallsToFix.addIfNotNull(ref.parent as? PsiMethodCallExpression)
                    }
                }
            }
        }

        val typeParameterList = newTypeParameterList(element)

        val psiFactory = KtPsiFactory(project)

        val (extension, bodyTypeToSelect) = runWriteAction {
            val extension = file.addAfter(element, outermostParent) as KtCallableDeclaration
            file.addAfter(psiFactory.createNewLine(), outermostParent)
            file.addAfter(psiFactory.createNewLine(), outermostParent)
            element.delete()

            extension.setReceiverType(containingClass!!)

            if (typeParameterList != null) {
                if (extension.typeParameterList != null) {
                    extension.typeParameterList!!.replace(typeParameterList)
                }
                else {
                    extension.addBefore(typeParameterList, extension.receiverTypeReference)
                    extension.addBefore(psiFactory.createWhiteSpace(), extension.receiverTypeReference)
                }
            }

            extension.modifierList?.getModifier(KtTokens.PROTECTED_KEYWORD)?.delete()
            extension.modifierList?.getModifier(KtTokens.ABSTRACT_KEYWORD)?.delete()
            extension.modifierList?.getModifier(KtTokens.OPEN_KEYWORD)?.delete()
            extension.modifierList?.getModifier(KtTokens.FINAL_KEYWORD)?.delete()

            if (isEffectivelyExpected && !extension.hasExpectModifier()) {
                extension.addModifier(KtTokens.EXPECT_KEYWORD)
            }

            var bodyTypeToSelect = GeneratedBodyType.NOTHING

            val bodyText = getFunctionBodyTextFromTemplate(
                project,
                if (extension is KtFunction) TemplateKind.FUNCTION else TemplateKind.PROPERTY_INITIALIZER,
                extension.name,
                extension.getReturnTypeReference()?.text ?: "Unit",
                extension.containingClassOrObject?.fqName
            )

            when (extension) {
                is KtFunction -> {
                    if (!extension.hasBody() && !isEffectivelyExpected) { //TODO: methods in PSI for setBody
                        extension.add(psiFactory.createBlock(bodyText))
                        bodyTypeToSelect = GeneratedBodyType.FUNCTION
                    }
                }

                is KtProperty -> {
                    val templateProperty =
                        psiFactory.createDeclaration<KtProperty>("var v: Any\nget()=$bodyText\nset(value){\n$bodyText\n}")

                    if (!isEffectivelyExpected) {
                        val templateGetter = templateProperty.getter!!
                        val templateSetter = templateProperty.setter!!

                        var getter = extension.getter
                        if (getter == null) {
                            getter = extension.addAfter(templateGetter, extension.typeReference) as KtPropertyAccessor
                            extension.addBefore(psiFactory.createNewLine(), getter)
                            bodyTypeToSelect = GeneratedBodyType.GETTER
                        }
                        else if (!getter.hasBody()) {
                            getter = getter.replace(templateGetter) as KtPropertyAccessor
                            bodyTypeToSelect = GeneratedBodyType.GETTER
                        }

                        if (extension.isVar) {
                            var setter = extension.setter
                            if (setter == null) {
                                setter = extension.addAfter(templateSetter, getter) as KtPropertyAccessor
                                extension.addBefore(psiFactory.createNewLine(), setter)
                                if (bodyTypeToSelect == GeneratedBodyType.NOTHING) {
                                    bodyTypeToSelect = GeneratedBodyType.SETTER
                                }
                            }
                            else if (!setter.hasBody()) {
                                setter.replace(templateSetter) as KtPropertyAccessor
                                if (bodyTypeToSelect == GeneratedBodyType.NOTHING) {
                                    bodyTypeToSelect = GeneratedBodyType.SETTER
                                }
                            }
                        }
                    }
                }
            }
            extension to bodyTypeToSelect
        }

        if (ktFilesToAddImports.isNotEmpty()) {
            extension.fqName?.let { fqName ->
                runWriteAction {
                    for (ktFile in ktFilesToAddImports) {
                        ktFile.addImport(fqName)
                    }
                }
            }
        }

        if (javaCallsToFix.isNotEmpty()) {
            val lightMethod = extension.toLightMethods().first()
            for (javaCallToFix in javaCallsToFix) {
                runWriteAction {
                    javaCallToFix.methodExpression.qualifierExpression?.let {
                        val argumentList = javaCallToFix.argumentList
                        argumentList.addBefore(it, argumentList.expressions.firstOrNull())
                    }

                    val newRef = javaCallToFix.methodExpression.bindToElement(lightMethod)
                    JavaCodeStyleManager.getInstance(project).shortenClassReferences(newRef)
                }
            }
        }

        return extension to bodyTypeToSelect
    }

    private fun askIfExpectedIsAllowed(): Boolean {
        return Messages.showYesNoDialog(
            KotlinBundle.message("do.you.want.to.make.new.extension.an.expected.declaration"), text, Messages.getQuestionIcon()
        ) == Messages.YES
    }

    private fun createExtensionCallableAndPrepareBodyToSelect(
        element: KtCallableDeclaration, allowExpected: Boolean = true
    ): Pair<KtCallableDeclaration, GeneratedBodyType> {
        val expectedDeclaration = liftToExpect(element) as? KtCallableDeclaration
        if (expectedDeclaration != null) {
            withExpectedActuals(element).filterIsInstance<KtCallableDeclaration>().forEach {
                if (it != element) {
                    processSingleDeclaration(it, allowExpected)
                }
            }
        }

        val classVisibility = element.containingClass()?.visibilityModifierType()
        val (extension, bodyTypeToSelect) = processSingleDeclaration(element, allowExpected)
        if (classVisibility != null && extension.visibilityModifier() == null) {
            runWriteAction {
                extension.addModifier(classVisibility)
            }
        }

        return extension to bodyTypeToSelect
    }

    private fun newTypeParameterList(member: KtCallableDeclaration): KtTypeParameterList? {
        val classElement =
            member.parents.match(KtClassBody::class, last = KtClassOrObject::class) ?: error("Can't typeMatch ${member.parent.parent}")
        val classParams = classElement.typeParameters
        if (classParams.isEmpty()) return null
        val allTypeParameters = classParams + member.typeParameters
        val text = allTypeParameters.joinToString(",", "<", ">") { it.textWithoutVariance() }
        return org.jetbrains.kotlin.psi.KtPsiFactory(member.project).createDeclaration<KtFunction>("fun $text foo()").typeParameterList
    }

    private fun KtTypeParameter.textWithoutVariance(): String {
        if (variance == Variance.INVARIANT) return text
        val copied = this.copy() as KtTypeParameter
        copied.modifierList?.getModifier(KtTokens.OUT_KEYWORD)?.delete()
        copied.modifierList?.getModifier(KtTokens.IN_KEYWORD)?.delete()
        return copied.text
    }

    private fun KtCallableDeclaration.setReceiverType(klass: KtClassOrObject) {
        val className = buildString {
            append(klass.name)
            if (klass.typeParameters.isNotEmpty()) {
                append(klass.typeParameters.joinToString(", ", "<", ">") {
                    it.name ?: ""
                })
            }
        }
        val typeReference = KtPsiFactory.contextual(this@setReceiverType).createType(className)
        val receiverReference = setReceiverTypeReference(typeReference) ?: return
        shortenReferences(receiverReference)
    }
}
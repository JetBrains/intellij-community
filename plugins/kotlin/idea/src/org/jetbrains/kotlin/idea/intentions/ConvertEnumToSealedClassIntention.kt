// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.core.getOrCreateCompanionObject
import org.jetbrains.kotlin.idea.util.withExpectedActuals
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

/**
 * Tests:
 * [org.jetbrains.kotlin.idea.intentions.IntentionTestGenerated.ConvertEnumToSealedClass]
 */
class ConvertEnumToSealedClassIntention : SelfTargetingRangeIntention<KtClass>(
    KtClass::class.java,
    KotlinBundle.lazyMessage("convert.to.sealed.class")
) {
    override fun applicabilityRange(element: KtClass): TextRange? {
        if (element.getClassKeyword() == null) return null
        val nameIdentifier = element.nameIdentifier ?: return null
        val enumKeyword = element.modifierList?.getModifier(KtTokens.ENUM_KEYWORD) ?: return null
        return TextRange(enumKeyword.startOffset, nameIdentifier.endOffset)
    }

    override fun applyTo(element: KtClass, editor: Editor?) {
        val name = element.name ?: return
        if (name.isEmpty()) return
        val doesSupportDataObjects = element.languageVersionSettings.supportsFeature(LanguageFeature.DataObjects)

        for (klass in element.withExpectedActuals()) {
            if (klass !is KtClass) continue

            val classDescriptor = klass.resolveToDescriptorIfAny() ?: continue
            val isExpect = classDescriptor.isExpect
            val isActual = classDescriptor.isActual

            klass.removeModifier(KtTokens.ENUM_KEYWORD)
            klass.addModifier(KtTokens.SEALED_KEYWORD)

            val psiFactory = KtPsiFactory(klass.project)

            val objects = mutableListOf<KtObjectDeclaration>()
            for (member in klass.declarations) {
                if (member !is KtEnumEntry) continue

                val obj = psiFactory.createDeclaration<KtObjectDeclaration>(
                    listOfNotNull(
                        "data".takeIf { doesSupportDataObjects },
                        "object",
                        member.name,
                    ).joinToString(" ")
                )

                val initializers = member.initializerList?.initializers ?: emptyList()
                if (initializers.isNotEmpty()) {
                    initializers.forEach { obj.addSuperTypeListEntry(psiFactory.createSuperTypeCallEntry("${klass.name}${it.text}")) }
                } else {
                    val defaultEntry = if (isExpect)
                        psiFactory.createSuperTypeEntry(name)
                    else
                        psiFactory.createSuperTypeCallEntry("$name()")
                    obj.addSuperTypeListEntry(defaultEntry)
                }

                if (isActual) {
                    obj.addModifier(KtTokens.ACTUAL_KEYWORD)
                }

                member.body?.let { body -> obj.add(body) }

                obj.addComments(member)

                member.delete()
                klass.addDeclaration(obj)

                objects.add(obj)
            }

            if (element.platform.isJvm()) {
                val enumEntryNames = objects.map { it.nameAsSafeName.asString() }
                val targetClassName = klass.name
                if (enumEntryNames.isNotEmpty() && targetClassName != null) {
                    val companionObject = klass.getOrCreateCompanionObject()
                    companionObject.addValuesFunction(targetClassName, enumEntryNames, psiFactory)
                    companionObject.addValueOfFunction(targetClassName, classDescriptor, enumEntryNames, psiFactory)
                }
            }

            klass.body?.let { body ->
                body.allChildren
                    .takeWhile { it !is KtDeclaration }
                    .firstOrNull { it.node.elementType == KtTokens.SEMICOLON }
                    ?.let { semicolon ->
                        val nonWhiteSibling = semicolon.siblings(forward = true, withItself = false).firstOrNull { it !is PsiWhiteSpace }
                        body.deleteChildRange(semicolon, nonWhiteSibling?.prevSibling ?: semicolon)
                        if (nonWhiteSibling != null) {
                            CodeStyleManager.getInstance(klass.project).reformat(nonWhiteSibling.firstChild ?: nonWhiteSibling)
                        }
                    }
            }
        }
    }

    private fun KtObjectDeclaration.addValuesFunction(targetClassName: String, enumEntryNames: List<String>, psiFactory: KtPsiFactory) {
        val functionText = "fun values(): Array<${targetClassName}> { return arrayOf(${enumEntryNames.joinToString()}) }"
        addDeclaration(psiFactory.createFunction(functionText))
    }

    private fun KtObjectDeclaration.addValueOfFunction(
        targetClassName: String,
        classDescriptor: ClassDescriptor,
        enumEntryNames: List<String>,
        psiFactory: KtPsiFactory
    ) {
        val classFqName = classDescriptor.fqNameSafe.asString()
        val functionText = buildString {
            append("fun valueOf(value: String): $targetClassName {")
            append("return when(value) {")
            enumEntryNames.forEach { append("\"$it\" -> $it\n") }
            append("else -> throw IllegalArgumentException(\"No object $classFqName.\$value\")")
            append("}")
            append("}")
        }
        addDeclaration(psiFactory.createFunction(functionText))
    }

    private fun KtObjectDeclaration.addComments(enumEntry: KtEnumEntry) {
        val (headComments, tailComments) = enumEntry.allChildren.toList().let { children ->
            children.takeWhile { it.isCommentOrWhiteSpace() } to children.takeLastWhile { it.isCommentOrWhiteSpace() }
        }
        if (headComments.isNotEmpty()) {
            val anchor = this.allChildren.first()
            headComments.forEach { addBefore(it, anchor) }
        }
        if (tailComments.isNotEmpty()) {
            val anchor = this.allChildren.last()
            tailComments.reversed().forEach { addAfter(it, anchor) }
        }
    }

    private fun PsiElement.isCommentOrWhiteSpace() = this is PsiComment || this is PsiWhiteSpace
}

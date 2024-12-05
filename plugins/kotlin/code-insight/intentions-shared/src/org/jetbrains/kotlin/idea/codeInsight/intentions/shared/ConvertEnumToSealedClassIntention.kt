// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.getOrCreateCompanionObject
import org.jetbrains.kotlin.idea.base.psi.relativeTo
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.ConvertEnumToSealedClassIntention.Context
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.search.ExpectActualUtils
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.siblings

/**
 * Tests:
 *
 * - [org.jetbrains.kotlin.idea.intentions.IntentionTestGenerated.ConvertEnumToSealedClass]
 * - [org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated.Other.testConvertActualEnumToSealedClass]
 * - [org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated.Other.testConvertExpectEnumToSealedClass]
 *
 * - [org.jetbrains.kotlin.idea.k2.intentions.tests.K2IntentionTestGenerated.ConvertEnumToSealedClass]
 * - [org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated.Other.testConvertActualEnumToSealedClass]
 * - [org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated.Other.testConvertExpectSealedClassToEnum]
 */
internal class ConvertEnumToSealedClassIntention : KotlinApplicableModCommandAction<KtClass, Context>(KtClass::class) {
    data class Context(
        val enumClassName: String,
        val supportsDataObjects: Boolean,
        val isJvmPlatform: Boolean,
        val classesInfo: Map<SmartPsiElementPointer<KtClass>, ClassInfo>,
    )

    data class ClassInfo(val isExpect: Boolean, val isActual: Boolean, val classFqName: String?)

    override fun getFamilyName(): @IntentionFamilyName String {
        return KotlinBundle.message("convert.to.sealed.class")
    }

    override fun getApplicableRanges(element: KtClass): List<TextRange> {
        if (element.getClassKeyword() == null) return emptyList()
        val nameIdentifier = element.nameIdentifier ?: return emptyList()
        val enumKeyword = element.modifierList?.getModifier(KtTokens.ENUM_KEYWORD) ?: return emptyList()
        return listOf(TextRange(enumKeyword.startOffset, nameIdentifier.endOffset).relativeTo(element))
    }

    context(KaSession)
    override fun prepareContext(element: KtClass): Context? {
        val enumClassName = element.name ?: return null
        if (enumClassName.isEmpty()) return null

        val supportsDataObjects = element.languageVersionSettings.supportsFeature(LanguageFeature.DataObjects)
        val isJvmPlatform = element.platform.isJvm()
        val expectActualClasses = ExpectActualUtils.withExpectedActuals(element)
        val classesData: Map<SmartPsiElementPointer<KtClass>, ClassInfo> = buildMap {
            for (klass in expectActualClasses) {
                if (klass !is KtClass) continue

                // Separate `analyze` call for every class, because they may be actuals from non-dependency modules
                analyze(klass) {
                    val symbol = klass.symbol as? KaClassSymbol ?: return@analyze
                    val classInfo = ClassInfo(symbol.isExpect, symbol.isActual, symbol.classId?.asFqNameString())
                    this@buildMap[klass.createSmartPointer()] = classInfo
                }
            }
        }

        return Context(enumClassName, supportsDataObjects, isJvmPlatform, classesData)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtClass,
        elementContext: Context,
        updater: ModPsiUpdater
    ) {
        for ((classPointer, classInfo) in elementContext.classesInfo) {
            val klass = classPointer.element ?: continue
            convertClass(updater.getWritable(klass), classInfo, elementContext)
        }
    }

    private fun convertClass(klass: KtClass, classInfo: ClassInfo, elementContext: Context) {
        val (enumClassName, supportsDataObjects, isJvmPlatform, _) = elementContext
        val (isExpect, isActual, classFqName) = classInfo

        klass.removeModifier(KtTokens.ENUM_KEYWORD)
        klass.addModifier(KtTokens.SEALED_KEYWORD)

        val psiFactory = KtPsiFactory(klass.project)

        val objects = mutableListOf<KtObjectDeclaration>()
        for (member in klass.declarations) {
            if (member !is KtEnumEntry) continue

            val obj = psiFactory.createDeclaration<KtObjectDeclaration>(
                listOfNotNull(
                    "data".takeIf { supportsDataObjects },
                    "object",
                    member.name,
                ).joinToString(" ")
            )

            val initializers = member.initializerList?.initializers ?: emptyList()
            if (initializers.isNotEmpty()) {
                for (initializer in initializers) {
                    val superTypeListEntry = psiFactory.createSuperTypeCallEntry("${klass.name}${initializer.text}")
                    obj.addSuperTypeListEntry(superTypeListEntry)
                }
            } else {
                val defaultEntry = if (isExpect) {
                    psiFactory.createSuperTypeEntry(enumClassName)
                } else {
                    psiFactory.createSuperTypeCallEntry("$enumClassName()")
                }
                obj.addSuperTypeListEntry(defaultEntry)
            }

            if (isActual) {
                obj.addModifier(KtTokens.ACTUAL_KEYWORD)
            }

            member.body?.let { obj.add(it) }

            obj.addComments(member)

            member.delete()
            klass.addDeclaration(obj)

            objects.add(obj)
        }

        if (isJvmPlatform) {
            val enumEntryNames = objects.map { it.nameAsSafeName.asString() }
            val targetClassName = klass.name
            if (enumEntryNames.isNotEmpty() && targetClassName != null) {
                val companionObject = klass.getOrCreateCompanionObject()
                companionObject.addValuesFunction(targetClassName, enumEntryNames, psiFactory)
                companionObject.addValueOfFunction(targetClassName, classFqName, enumEntryNames, psiFactory)
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

    private fun KtObjectDeclaration.addValuesFunction(targetClassName: String, enumEntryNames: List<String>, psiFactory: KtPsiFactory) {
        val functionText = "fun values(): Array<${targetClassName}> { return arrayOf(${enumEntryNames.joinToString()}) }"
        addDeclaration(psiFactory.createFunction(functionText))
    }

    private fun KtObjectDeclaration.addValueOfFunction(
        targetClassName: String,
        classFqName: String?,
        enumEntryNames: List<String>,
        psiFactory: KtPsiFactory
    ) {
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

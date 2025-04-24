// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.analysis.AnalysisBundle.message
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.CommandProvider
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider.registerReferenceFixes
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.psi.stubs.elements.KtNameReferenceExpressionElementType
import javax.swing.Icon


internal class KotlinCreateFromUsagesCommandProvider : CommandProvider {
    override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
        val editor = context.editor
        if (InjectedLanguageEditorUtil.getTopLevelEditor(editor) != editor) return emptyList()
        if (context.isReadOnly) return emptyList()

        val originalPsiFile = context.originalPsiFile
        val offset = context.originalOffset
        var psiElement = originalPsiFile.findElementAt(offset - 1)
        val elementType = psiElement.elementType
        if ((elementType is KtSingleValueToken && elementType.value == ".") ||
            elementType == KtTokens.IDENTIFIER
        ) {
            psiElement = psiElement?.parent
            if (psiElement.elementType is KtNameReferenceExpressionElementType) {
                psiElement = psiElement?.parent
            }
        }
        if (psiElement !is KtDotQualifiedExpression) return emptyList()
        val qualifier = psiElement.receiverExpression
        val psiClass = analyze(qualifier) {
            (qualifier.expressionType as? KaClassType)?.symbol?.psi
        } as? KtClass
        if (psiClass?.isWritable != true) return emptyList()
        val text = (psiElement.selectorExpression as? KtNameReferenceExpression)?.text
        if (text != null && !PsiNameHelper.getInstance(context.project)
                .isIdentifier(text)
        ) return emptyList()
        return listOf(KotlinCreateFromUsagesCompletionCommand(psiClass))
    }
}

internal class KotlinCreateFromUsagesCompletionCommand(val ktClass: KtClass) : CompletionCommand() {

    private val methodNames: Set<String> =
        analyze(ktClass) {
            (ktClass.symbol as? KaClassSymbol)?.memberScope?.declarations?.map { it.name?.identifier }?.filterNotNull()?.toSet()
        } ?: emptySet()

    override val name: String
        get() = "Create method from usage"
    override val i18nName: @Nls String
        get() = QuickFixBundle.message("create.method.from.usage.family")
    override val icon: Icon?
        get() = null

    override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
        val fileDocument = psiFile.fileDocument
        var currentOffset = offset
        val previousElement = if (currentOffset >= 0) psiFile.findElementAt(currentOffset - 1) else null
        WriteAction.run<RuntimeException> {
            if (fileDocument.charsSequence[currentOffset - 1] == '.') {
                fileDocument.insertString(currentOffset, "method")
                currentOffset = currentOffset + 6
            }
            if (previousElement == null || PsiTreeUtil.nextLeaf(previousElement)?.text?.startsWith("(") != true) {
                fileDocument.insertString(currentOffset, "()")
            }
            PsiDocumentManager.getInstance(psiFile.project).commitDocument(fileDocument)
        }

        val psiElement = psiFile.findElementAt(currentOffset) ?: return

        val expression: KtCallExpression = psiElement.parentOfType<KtCallExpression>() ?: return
        val intentions = mutableListOf<IntentionAction>()

        val registrar: QuickFixActionRegistrar = object : QuickFixActionRegistrar {
            override fun register(action: IntentionAction) {
                intentions.add(action)
            }

            override fun register(
                fixRange: TextRange,
                action: IntentionAction,
                key: HighlightDisplayKey?
            ) {
                intentions.add(action)
            }
        }
        val ref = (expression.getQualifiedElementSelector() as? KtNameReferenceExpression)?.reference ?: return
        runWithModalProgressBlocking(psiFile.project, message("scanning.scope.progress.title")) {
            readAction {
                registerReferenceFixes(ref, registrar)
            }
        }
        val action = intentions.find {
            //todo temporary solution. bad filtration, but right now I don't want to change kotlin for command completion
            it.javaClass.name == "org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction" &&
                    it.text.contains(KotlinBundle.message("text.member"))
        } ?: return
        ShowIntentionActionsHandler.chooseActionAndInvoke(psiFile, editor, action, name)
    }

    override fun customPrefixMatcher(prefix: String): PrefixMatcher? {
        return AlwaysMatchingCamelHumpMatcher(prefix, ktClass.project, methodNames)
    }

    override val priority: Int?
        get() = 500
}

private class AlwaysMatchingCamelHumpMatcher(
    prefix: String,
    private val project: Project,
    private val predefinedNames: Set<String>,
) : CamelHumpMatcher(prefix) {
    override fun prefixMatches(name: String): Boolean {
        return prefix == "" || PsiNameHelper.getInstance(project).isIdentifier(prefix) && !predefinedNames.contains(prefix)
    }

    override fun isStartMatch(name: String?) = name == null || prefixMatches(name)
    override fun cloneWithPrefix(prefix: String) =
        if (prefix == this.prefix) this else AlwaysMatchingCamelHumpMatcher(prefix, project, predefinedNames)
}
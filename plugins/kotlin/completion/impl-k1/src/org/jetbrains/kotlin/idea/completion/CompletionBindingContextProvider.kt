// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.*
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyzeInContext
import org.jetbrains.kotlin.idea.caches.trackers.KotlinCodeBlockModificationListener
import org.jetbrains.kotlin.idea.caches.trackers.PureKotlinCodeBlockModificationListener
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.CompositeBindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getDataFlowInfoBefore
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import java.lang.ref.SoftReference
import java.util.*

@Service(Service.Level.PROJECT)
class CompletionBindingContextProvider(project: Project) {
    private val LOG = Logger.getInstance(CompletionBindingContextProvider::class.java)

    @get:TestOnly
    var TEST_LOG: StringBuilder? = null

    companion object {
        fun getInstance(project: Project): CompletionBindingContextProvider = project.service()

        var ENABLED = true
    }

    private class CompletionData(
        val container: KtExpression,
        val prevStatement: KtExpression?,
        val psiElementsBeforeAndAfter: List<PsiElementData>,
        val bindingContext: BindingContext,
        val moduleDescriptor: ModuleDescriptor,
        val statementResolutionScope: LexicalScope,
        val statementDataFlowInfo: DataFlowInfo,
        val debugText: String
    ) {
        init {
          require(container is KtBlockExpression || container is KtDeclarationWithBody) {
              "Container was of class ${container::class}"
          }
        }
    }

    private data class PsiElementData(val element: PsiElement, val level: Int)

    private class DataHolder {
        private var reference: SoftReference<CompletionData>? = null

        var data: CompletionData?
            get() = reference?.get()
            set(value) {
                reference = value?.let { SoftReference(it) }
            }
    }

    private var prevCompletionDataCache: CachedValue<DataHolder> = CachedValuesManager.getManager(project).createCachedValue(
        {
            CachedValueProvider.Result.create(
                DataHolder(),
                KotlinCodeBlockModificationListener.getInstance(project).kotlinOutOfCodeBlockTracker
            )
        },
        false
    )


    fun getBindingContext(position: PsiElement, resolutionFacade: ResolutionFacade): BindingContext = if (ENABLED) {
        _getBindingContext(position, resolutionFacade)
    } else {
        resolutionFacade.analyze(position.parentsWithSelf.firstIsInstance<KtElement>(), BodyResolveMode.PARTIAL_FOR_COMPLETION)
    }

    private fun _getBindingContext(position: PsiElement, resolutionFacade: ResolutionFacade): BindingContext {
        val (inStatement, container) =
            position.findStatementInBlock()
                ?: position.findSingleExpressionBody()
                ?: (null to null)

        val prevStatement = inStatement?.siblings(forward = false, withItself = false)?.firstIsInstanceOrNull<KtExpression>()
        val modificationScope = inStatement?.let { PureKotlinCodeBlockModificationListener.getInsideCodeBlockModificationScope(it)?.element }

        val psiElementsBeforeAndAfter = modificationScope?.let { collectPsiElementsBeforeAndAfter(modificationScope, inStatement) }

        val prevCompletionData = prevCompletionDataCache.value.data
        when {
            prevCompletionData == null ->
                log("No up-to-date data from previous completion\n")
            container != prevCompletionData.container ->
                log("Not in the same container\n")
            prevStatement != prevCompletionData.prevStatement ->
                log("Previous statement is not the same\n")
            psiElementsBeforeAndAfter != prevCompletionData.psiElementsBeforeAndAfter ->
                log("PSI-tree has changed inside current scope\n")
            prevCompletionData.moduleDescriptor != resolutionFacade.moduleDescriptor ->
                log("ModuleDescriptor has been reset")
            inStatement.isTooComplex() ->
                log("Current statement is too complex to use optimization\n")
            else -> {
                log("Statement position is the same - analyzing only one statement:\n${inStatement.text.prependIndent("    ")}\n")
                LOG.debug("Reusing data from completion of \"${prevCompletionData.debugText}\"")

                //TODO: expected type?
                val statementContext = inStatement.analyzeInContext(
                    scope = prevCompletionData.statementResolutionScope,
                    contextExpression = container,
                    dataFlowInfo = prevCompletionData.statementDataFlowInfo,
                    isStatement = true
                )
                // we do not update prevCompletionDataCache because the same data should work
                return CompositeBindingContext.create(listOf(statementContext, prevCompletionData.bindingContext))
            }
        }

        val bindingContext =
            resolutionFacade.analyze(position.parentsWithSelf.firstIsInstance<KtElement>(), BodyResolveMode.PARTIAL_FOR_COMPLETION)
        prevCompletionDataCache.value.data = if (container != null && modificationScope != null) {
            val resolutionScope = inStatement.getResolutionScope(bindingContext, resolutionFacade)
            val dataFlowInfo = bindingContext.getDataFlowInfoBefore(inStatement)
            CompletionData(
                container,
                prevStatement,
                psiElementsBeforeAndAfter!!,
                bindingContext,
                resolutionFacade.moduleDescriptor,
                resolutionScope,
                dataFlowInfo,
                debugText = position.text
            )
        } else {
            null
        }

        return bindingContext
    }

    private fun log(message: String) {
        TEST_LOG?.append(message)
        LOG.debug(message)
    }

    private fun collectPsiElementsBeforeAndAfter(scope: PsiElement, statement: KtExpression): List<PsiElementData> {
        return ArrayList<PsiElementData>().apply { addElementsInTree(scope, 0, statement) }
    }

    private fun MutableList<PsiElementData>.addElementsInTree(root: PsiElement, initialLevel: Int, skipSubtree: PsiElement) {
        if (root == skipSubtree) return
        add(PsiElementData(root, initialLevel))
        var child = root.firstChild
        while (child != null) {
            if (child !is PsiWhiteSpace && child !is PsiComment && child !is PsiErrorElement) {
                addElementsInTree(child, initialLevel + 1, skipSubtree)
            }
            child = child.nextSibling
        }
    }

    private fun PsiElement.findStatementInBlock(): Pair<KtExpression, KtBlockExpression>? =
        parentsOfType<KtExpression>().firstNotNullOfOrNull { expression ->
            val parent = expression.parent
            if (parent is KtBlockExpression) {
                expression to parent
            } else {
                null
            }
        }

    private fun PsiElement.findSingleExpressionBody(): Pair<KtExpression, KtDeclaration>? =
        parentsOfType<KtExpression>().firstNotNullOfOrNull { expression ->
            val parent = expression.parent
            if (parent is KtDeclarationWithBody && parent.bodyExpression == expression) {
                expression to parent
            } else {
                null
            }
        }

    private fun KtExpression.isTooComplex(): Boolean {
        return anyDescendantOfType<KtBlockExpression> { it.statements.size > 1 }
    }
}

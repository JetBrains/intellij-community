// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.completion

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.daemon.impl.quickfix.ReferenceNameExpression
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.VariableNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.util.castSafelyTo
import icons.JetgroovyIcons
import org.jetbrains.plugins.groovy.ext.ginq.ast.*
import org.jetbrains.plugins.groovy.ext.ginq.joins
import org.jetbrains.plugins.groovy.ext.ginq.windowFunctions
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.impl.getArguments

object GinqCompletionUtils {

  fun CompletionResultSet.addFromAndSelect(macroCall: GrMethodCall) {
    val closure = macroCall.getArguments()?.filterIsInstance<ExpressionArgument>()?.find { it.expression is GrClosableBlock } ?: return
    var hasFrom = false
    var hasSelect = false
    closure.expression.accept(object : GroovyRecursiveElementVisitor() {
      override fun visitMethodCall(call: GrMethodCall) {
        super.visitMethodCall(call)
        val invoked = call.invokedExpression.castSafelyTo<GrReferenceExpression>()
        if (invoked?.referenceName == "from") hasFrom = true
        if (invoked?.referenceName == "select") hasSelect = true
      }
    })
    if (!hasFrom) {
      addElement(LookupElementBuilder.create("from").bold().withInsertHandler(dataSourceInsertHandler))
    }
    if (!hasSelect) {
      addElement(LookupElementBuilder.create("select ").bold())
    }
  }

  fun CompletionResultSet.addGeneralGroovyResults(position: PsiElement, offset: Int, ginq: GinqExpression, top: GrMethodCall) {
    if (position.parent?.parent is GrParenthesizedExpression) {
      addElement(LookupElementBuilder.create("from").bold().withInsertHandler(dataSourceInsertHandler))
    }
    val bindings = position.ginqParents(top, top.getStoredGinq()!!)
      .flatMap { gq ->  gq.getDataSourceFragments().map { it.alias }.filter { it.endOffset < offset } }
    for (binding in bindings) {
      val name = binding.referenceName ?: continue
      val bindingItem = LookupElementBuilder.create(name)
        .withPsiElement(binding)
        .withTypeText(binding.type?.presentableText)
        .withIcon(JetgroovyIcons.Groovy.Variable)
      addElement(PrioritizedLookupElement.withPriority(bindingItem, 1.0))
    }
    if (ginq.select.projections.any { PsiTreeUtil.isAncestor(it.aggregatedExpression, position, false) }) {
      for ((windowName, signature) in windowFunctions) {
        val lookupElement = LookupElementBuilder.create(windowName)
          .withIcon(JetgroovyIcons.Groovy.Method)
          .withTypeText(signature.returnType.substringAfterLast('.'))
          .withTailText(signature.parameters.joinToString(", ", "(", ")") { it.second.substringAfterLast('.') })
          .withInsertHandler(windowInsertHandler)
        addElement(lookupElement)
      }
    }
  }

  fun CompletionResultSet.addGinqKeywords(ginq: GinqExpression, offset: Int) {
    val closestFragmentUp = ginq.getQueryFragments().minByOrNull {
      val endOffset = it.keyword.endOffset
      if (endOffset <= offset) {
        offset - endOffset
      }
      else {
        Int.MAX_VALUE
      }
    }
    if (closestFragmentUp != null) {
      val joinStartCondition: (GinqQueryFragment) -> Boolean = {
        it is GinqFromFragment || it is GinqOnFragment || (it is GinqJoinFragment && it.keyword.text == "crossjoin")
      }
      if (joinStartCondition(closestFragmentUp)) {
        // todo: new binding name should be inferred
        joins.forEach { addElement(LookupElementBuilder.create(it).bold().withInsertHandler(dataSourceInsertHandler)) }
      }
      if (closestFragmentUp is GinqJoinFragment && closestFragmentUp.onCondition == null && closestFragmentUp.keyword.text != "crossjoin") {
        addElement(LookupElementBuilder.create("on ").bold())
      }
      if (joinStartCondition(closestFragmentUp) && ginq.where == null) {
        addElement(LookupElementBuilder.create("where ").bold())
      }
      val groupByCondition: (GinqQueryFragment) -> Boolean = { joinStartCondition(it) || it is GinqWhereFragment }
      if (groupByCondition(closestFragmentUp) && ginq.groupBy == null) {
        addElement(LookupElementBuilder.create("groupby ").bold())
      }
      if (closestFragmentUp is GinqGroupByFragment && closestFragmentUp.having == null) {
        addElement(LookupElementBuilder.create("having ").bold())
      }
      val orderByCondition: (GinqQueryFragment) -> Boolean = {
        groupByCondition(it) || it is GinqGroupByFragment || it is GinqHavingFragment
      }
      if (orderByCondition(closestFragmentUp) && ginq.orderBy == null) {
        addElement(LookupElementBuilder.create("orderby ").bold())
      }
      if ((orderByCondition(closestFragmentUp) || closestFragmentUp is GinqOrderByFragment) && ginq.limit == null) {
        addElement(LookupElementBuilder.create("limit ").bold())
      }
    }
  }

  fun CompletionResultSet.addOverKeywords(ginq: GinqExpression, position: PsiElement) {
    val overRoots = ginq.select.projections.flatMap { partition ->
      partition.windows
    }
    val overRoot = overRoots.find {
      PsiTreeUtil.isAncestor(it.overKw.parent.parent.castSafelyTo<GrMethodCall>()?.argumentList, position, false)
    }
    if (overRoot != null) {
      if (overRoot.partitionKw == null) {
        addElement(LookupElementBuilder.create("partitionby ").bold())
      }
      if (overRoot.orderBy?.keyword == null) {
        addElement(LookupElementBuilder.create("orderby ").bold())
      }
      if (overRoot.rowsOrRangeKw == null) {
        addElement(LookupElementBuilder.create("rows ").bold())
        addElement(LookupElementBuilder.create("range ").bold())
      }
    }
  }
}

private val dataSourceInsertHandler = InsertHandler<LookupElement> { context, lookupItem ->
  val item = lookupItem.lookupString
  val requiresOn = item != "from" && item != "crossjoin"
  val template = TemplateManager.getInstance(context.project)
    .createTemplate("ginq_data_source_$item", "ginq",
                    "$item \$NAME$ in \$DATA_SOURCE$${if (requiresOn) " on \$COND$" else ""}\$END$")
  template.addVariable("NAME", ReferenceNameExpression(emptyArray(), "x"), true)
  template.addVariable("DATA_SOURCE", VariableNode("data source", null), true)
  if (requiresOn) {
    template.addVariable("COND", VariableNode("on condition", null), true)
  }
  val editor = context.editor
  editor.document.deleteString(context.startOffset, context.tailOffset)
  TemplateManager.getInstance(context.project).startTemplate(editor, template)
}

private val windowInsertHandler = InsertHandler<LookupElement> { context, lookupItem ->
  val item = lookupItem.lookupString
  val zeroArg = item in windowFunctions.filter { (name, sign) -> sign.parameters.isEmpty() }.keys
  val template = TemplateManager.getInstance(context.project)
    .createTemplate("ginq_window_$item", "ginq",
                    "($item(${if (zeroArg) "" else "\$ARG$"}) over (\$END$))")
  if (!zeroArg) {
    template.addVariable("ARG", VariableNode("argument", null), true)
  }
  val editor = context.editor
  editor.document.deleteString(context.startOffset, context.tailOffset)
  TemplateManager.getInstance(context.project).startTemplate(editor, template)
}

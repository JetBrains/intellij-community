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
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.asSafely
import icons.JetgroovyIcons
import org.jetbrains.plugins.groovy.ext.ginq.*
import org.jetbrains.plugins.groovy.ext.ginq.ast.*
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.impl.getArguments

object GinqCompletionUtils {

  fun CompletionResultSet.addFromSelectShutdown(root: GinqRootPsiElement, position: PsiElement) {
    if (position.parentOfType<GrMethodCall>()?.callRefName == KW_SHUTDOWN) {
      addElement(lookupElement(KW_IMMEDIATE))
      addElement(lookupElement(KW_ABORT))
      stopHere()
      return
    }
    val ginqCodeContainer = when (root) {
      is GinqRootPsiElement.Call -> {
        root.psi.getArguments()?.filterIsInstance<ExpressionArgument>()?.find { it.expression is GrClosableBlock }?.expression ?: return
      }
      is GinqRootPsiElement.Method -> {
        root.psi.block ?: return
      }
    }
    var hasFrom = false
    var hasSelect = false
    ginqCodeContainer.accept(object : GroovyRecursiveElementVisitor() {
      override fun visitMethodCall(call: GrMethodCall) {
        super.visitMethodCall(call)
        if (call.callRefName == KW_FROM) hasFrom = true
        if (call.callRefName == KW_SELECT) hasSelect = true
      }
    })
    if (!hasFrom) addElement(lookupElement(KW_FROM, root.psi, root))
    if (!hasSelect) addElement(lookupElement(KW_SELECT))
    addElement(lookupElement(KW_SHUTDOWN))
  }

  fun CompletionResultSet.addGeneralGroovyResults(position: PsiElement, offset: Int, ginq: GinqExpression, root: GinqRootPsiElement) {
    if (position.parent?.parent is GrParenthesizedExpression) {
      addElement(lookupElement(KW_FROM, position, root))
    }
    val bindings = position.ginqParents(root, root.psi.getStoredGinq()!!)
      .flatMap { gq -> gq.getDataSourceFragments().map { it.alias }.filter { it.endOffset < offset } }
    for (binding in bindings) {
      val name = binding.referenceName ?: continue
      val bindingItem = LookupElementBuilder.create(name)
        .withPsiElement(binding)
        .withTypeText(binding.type?.presentableText)
        .withIcon(JetgroovyIcons.Groovy.Variable)
      addElement(PrioritizedLookupElement.withPriority(bindingItem, 1.0))
    }
    if (ginq.select?.projections?.any { PsiTreeUtil.isAncestor(it.aggregatedExpression, position, false) } == true) {
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

  fun CompletionResultSet.addGinqKeywords(ginq: GinqExpression, offset: Int, root: GinqRootPsiElement, position: PsiElement) {
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
        it is GinqFromFragment || it is GinqOnFragment || (it is GinqJoinFragment && it.keyword.text == KW_CROSSJOIN)
      }
      if (joinStartCondition(closestFragmentUp)) {
        addAllElements(JOINS.map { lookupElement(it, position, root) })
      }
      if (closestFragmentUp is GinqJoinFragment && closestFragmentUp.onCondition == null && closestFragmentUp.keyword.text != KW_CROSSJOIN) {
        addElement(lookupElement(KW_ON))
      }
      if (joinStartCondition(closestFragmentUp) && ginq.where == null) {
        addElement(lookupElement(KW_WHERE))
      }
      val groupByCondition: (GinqQueryFragment) -> Boolean = { joinStartCondition(it) || it is GinqWhereFragment }
      if (groupByCondition(closestFragmentUp) && ginq.groupBy == null) {
        addElement(lookupElement(KW_GROUPBY))
      }
      if (closestFragmentUp is GinqGroupByFragment && closestFragmentUp.having == null) {
        addElement(lookupElement(KW_HAVING))
      }
      val orderByCondition: (GinqQueryFragment) -> Boolean = {
        groupByCondition(it) || it is GinqGroupByFragment || it is GinqHavingFragment
      }
      if (orderByCondition(closestFragmentUp) && ginq.orderBy == null) {
        addElement(lookupElement(KW_ORDERBY))
      }
      val limitCondition: (GinqQueryFragment) -> Boolean = {
        orderByCondition(closestFragmentUp) || closestFragmentUp is GinqOrderByFragment
      }
      if (limitCondition(closestFragmentUp) && ginq.limit == null) {
        addElement(lookupElement(KW_LIMIT))
      }
      if ((limitCondition(closestFragmentUp) || closestFragmentUp is GinqLimitFragment) && ginq.select == null) {
        addElement(lookupElement(KW_SELECT))
      }
    }
  }

  fun CompletionResultSet.addOrderbyDependentKeywords(position: PsiElement) {
    val call = position.parentOfType<GrMethodCall>() ?: return
    val callText = call.callRefName
    if (callText == KW_ORDERBY) {
      addElement(lookupElement(KW_ASC, false))
      addElement(lookupElement(KW_DESC, false))
    }
    if (callText == KW_ASC || callText == KW_DESC) {
      addElement(lookupElement(KW_NULLSFIRST, false))
      addElement(lookupElement(KW_NULLSLAST, false))
    }
  }

  fun CompletionResultSet.addOverKeywords(ginq: GinqExpression, position: PsiElement) {
    val overRoots = ginq.select?.projections?.flatMap { partition ->
      partition.windows
    } ?: return
    val overRoot = overRoots.find {
      PsiTreeUtil.isAncestor(it.overKw.parent.parent.asSafely<GrMethodCall>()?.argumentList, position, false)
    }
    if (overRoot != null) {
      if (overRoot.partitionKw == null) {
        addElement(lookupElement("partitionby"))
      }
      if (overRoot.orderBy?.keyword == null) {
        addElement(lookupElement(KW_ORDERBY))
      }
      if (overRoot.rowsOrRangeKw == null) {
        addElement(lookupElement("rows"))
        addElement(lookupElement("range"))
      }
    }
  }
}

private fun getDataSourceInsertHandler(position: PsiElement, root: GinqRootPsiElement) = InsertHandler<LookupElement> { context, lookupItem ->
  val parentIdentifiers = gatherIdentifiers(position, root)
  val item = lookupItem.lookupString
  val requiresOn = !item.contains(KW_FROM) && !item.contains(KW_CROSSJOIN)
  val template = TemplateManager.getInstance(context.project)
    .createTemplate("ginq_data_source_$item", "ginq", "$item\$NAME$ in \$DATA_SOURCE$${if (requiresOn) " on \$COND$" else ""}\$END$")
  template.addVariable("NAME", ReferenceNameExpression(emptyArray(), generateName(parentIdentifiers)), true)
  template.addVariable("DATA_SOURCE", VariableNode("data source", null), true)
  if (requiresOn) {
    template.addVariable("COND", VariableNode("on condition", null), true)
  }
  val editor = context.editor
  editor.document.deleteString(context.startOffset, context.tailOffset)
  TemplateManager.getInstance(context.project).startTemplate(editor, template)
}

private fun gatherIdentifiers(position: PsiElement, root: GinqRootPsiElement) : List<String> {
  val topGinq = getTopParsedGinqTree(root) ?: return emptyList()
  val parents = position.ginqParents(root, topGinq)
  val parentGinqIdentifiers = parents.drop(1)
    .flatMap { ginq -> ginq.getDataSourceFragments().mapNotNull { it.alias.referenceName } }
  val localIdentifiers = parents.firstOrNull()?.getDataSourceFragments()?.filter {
    (it as GinqQueryFragment).keyword.startOffset < position.startOffset
  }?.mapNotNull { it.alias.referenceName } ?: emptyList()
  return parentGinqIdentifiers.toList() + localIdentifiers
}

private fun generateName(identifiers: List<String>) : String {
  for (i in 0..Int.MAX_VALUE) {
    val identifier = if (i == 0) "x" else "x$i"
    if (identifier !in identifiers) {
      return identifier
    }
  }
  return "y"
}

private val windowInsertHandler = InsertHandler<LookupElement> { context, lookupItem ->
  val item = lookupItem.lookupString
  val zeroArg = item in windowFunctions.filter { (_, sign) -> sign.parameters.isEmpty() }.keys
  val template = TemplateManager.getInstance(context.project)
    .createTemplate("ginq_window_$item", "ginq", "($item(${if (zeroArg) "" else "\$ARG$"}) over (\$END$))")
  if (!zeroArg) {
    template.addVariable("ARG", VariableNode("argument", null), true)
  }
  val editor = context.editor
  editor.document.deleteString(context.startOffset, context.tailOffset)
  TemplateManager.getInstance(context.project).startTemplate(editor, template)
}

private fun lookupElement(keyword: String, position: PsiElement, root: GinqRootPsiElement): LookupElementBuilder {
  return lookupElement(keyword).let {
    if (keyword == KW_FROM || keyword in JOINS) it.withInsertHandler(getDataSourceInsertHandler(position, root)) else it
  }
}

private fun lookupElement(keyword: String, addSpace: Boolean = true): LookupElementBuilder {
  return LookupElementBuilder.create("$keyword${if (addSpace) " " else ""}").bold()
}
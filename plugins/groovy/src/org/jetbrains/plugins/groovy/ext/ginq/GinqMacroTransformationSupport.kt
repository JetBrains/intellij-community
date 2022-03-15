// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.quickfix.ReferenceNameExpression
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.VariableNode
import com.intellij.formatting.Block
import com.intellij.formatting.Indent
import com.intellij.formatting.Wrap
import com.intellij.formatting.WrapType
import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import com.intellij.refactoring.suggested.endOffset
import com.intellij.util.castSafelyTo
import com.intellij.util.lazyPub
import icons.JetgroovyIcons
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.ext.ginq.ast.*
import org.jetbrains.plugins.groovy.ext.ginq.formatting.GinqFragmentBlock
import org.jetbrains.plugins.groovy.ext.ginq.types.GrSyntheticNamedRecordClass
import org.jetbrains.plugins.groovy.formatter.FormattingContext
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlockGenerator
import org.jetbrains.plugins.groovy.formatter.blocks.SyntheticGroovyBlock
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable
import org.jetbrains.plugins.groovy.lang.resolve.*
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.impl.getArguments
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import org.jetbrains.plugins.groovy.lang.typing.box
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroTransformationSupportEx

internal class GinqMacroTransformationSupport : GroovyMacroTransformationSupportEx {

  override fun isApplicable(macro: PsiMethod): Boolean {
    return macro.name in ginqMethods && macro.containingClass?.name == "GinqGroovyMethods"
  }

  override fun computeHighlighting(macroCall: GrCall): List<HighlightInfo> {
    val errors = getParsedGinqErrors(macroCall).mapNotNull {
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(it.first)
        .descriptionAndTooltip(it.second)
        .textAttributes(CodeInsightColors.ERRORS_ATTRIBUTES).create()
    }
    val keywords = mutableListOf<PsiElement>()
    val softKeywords = mutableListOf<PsiElement>()
    val warnings = mutableListOf<Pair<PsiElement, @Nls String>>()
    macroCall.accept(object : GroovyRecursiveElementVisitor() {
      override fun visitElement(element: GroovyPsiElement) {
        val nestedGinq = element.getStoredGinq()
        if (nestedGinq != null) {
          keywords.addAll(nestedGinq.getQueryFragments().map { it.keyword })
          keywords.addAll(nestedGinq.select.projections.flatMap { projection ->
            projection.windows.flatMap { listOfNotNull(it.overKw, it.rowsOrRangeKw, it.partitionKw, it.orderBy?.keyword) }
          })
          warnings.addAll(getTypecheckingWarnings(nestedGinq))
          softKeywords.addAll((nestedGinq.orderBy?.sortingFields?.mapNotNull { it.orderKw } ?: emptyList()) +
                              (nestedGinq.orderBy?.sortingFields?.mapNotNull { it.nullsKw } ?: emptyList()) +
                              (nestedGinq.select.projections.flatMap { projection ->
                                projection.windows.flatMap { window ->
                                  window.orderBy?.sortingFields?.flatMap {
                                    listOfNotNull(it.orderKw, it.nullsKw)
                                  } ?: emptyList()
                                }
                              }))
        }
        super.visitElement(element)
      }
    })
    return keywords.mapNotNull {
      HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(it).textAttributes(GroovySyntaxHighlighter.KEYWORD).create()
    } + softKeywords.mapNotNull {
      val key = if (it.parent is GrMethodCall) GroovySyntaxHighlighter.STATIC_METHOD_ACCESS else GroovySyntaxHighlighter.STATIC_FIELD
      HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(it).textAttributes(key).create()
    } + warnings.mapNotNull {
      HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(it.first)
        .severity(HighlightSeverity.WARNING).textAttributes(CodeInsightColors.WARNINGS_ATTRIBUTES).descriptionAndTooltip(it.second).create()
    } + errors
  }

  private fun getTypecheckingWarnings(ginq: GinqExpression): Collection<Pair<PsiElement, @Nls String>> {
    val dataSourceFragments = ginq.getDataSourceFragments()
    val filteringFragments = ginq.getFilterFragments()
    val filterResults = filteringFragments.mapNotNull { fragment ->
      val type = fragment.filter.type
      val parentCall = fragment.filter.parentOfType<GrMethodCall>()?.parentOfType<GrMethodCall>()?.invokedExpression?.castSafelyTo<GrReferenceExpression>()?.takeIf { it.referenceName == "exists" }
      if (type != PsiType.BOOLEAN && type?.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN) != true && parentCall == null) {
        fragment.filter to GroovyBundle.message("ginq.error.message.boolean.condition.expected")
      }
      else {
        null
      }
    }
    val dataSourceResults = dataSourceFragments.mapNotNull {
      val type = inferDataSourceComponentType(it.dataSource.type)
      if (type == null) {
        it.dataSource to GroovyBundle.message("ginq.error.message.container.expected")
      }
      else {
        null
      }
    }
    return filterResults + dataSourceResults
  }

  override fun computeType(macroCall: GrMethodCall, expression: GrExpression): PsiType? {
    val ginq = if (expression == macroCall) getParsedGinqTree(macroCall) else expression.getStoredGinq()
    val facade = JavaPsiFacade.getInstance(macroCall.project)
    if (ginq != null) {
      val invokedCall = macroCall.invokedExpression.castSafelyTo<GrReferenceExpression>()?.referenceName
      val container = if (invokedCall == "GQL") CommonClassNames.JAVA_UTIL_LIST else if (invokedCall == "GQ") ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE else null
      if (container != null) {
        val singleProjection = ginq.select.projections.singleOrNull()?.takeIf { it.alias == null }
        val componentType = if (singleProjection != null) {
          singleProjection.aggregatedExpression.type ?: PsiType.NULL
        }
        else {
          val namedRecord = facade.findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_NAMED_RECORD, expression.resolveScope)
          namedRecord?.let { GrSyntheticNamedRecordClass(ginq, it).type() } ?: PsiType.NULL
        }
        return facade.findClass(container, macroCall.resolveScope)?.let {
          val actualComponentType = if (componentType == PsiType.NULL) PsiWildcardType.createUnbounded(expression.manager) else componentType.box(expression)
          facade.elementFactory.createType(it, actualComponentType)
        }
      }
    }
    if (expression is GrMethodCall && expression.resolveMethod().castSafelyTo<OriginInfoAwareElement>()?.originInfo == OVER_ORIGIN_INFO) {
      return expression.invokedExpression.castSafelyTo<GrReferenceExpression>()?.qualifierExpression?.type
    }
    if (expression is GrReferenceExpression) {
      val tree = getParsedGinqTree(macroCall) ?: return null
      if (expression.referenceName == "_g") {
        val custom = resolveToCustomMember(expression, "_g", tree)
        if (custom != null) {
          return custom.type
        }
      }
      val resolved = expression.staticReference.resolve()
      val dataSourceFragment = expression.ginqParents(macroCall,
                                                      tree).firstNotNullOfOrNull { parentGinq -> parentGinq.getDataSourceFragments().find { it.alias == resolved } }
      if (dataSourceFragment != null) {
        return inferDataSourceComponentType(dataSourceFragment.dataSource.type)
      }
    }
    return null
  }

  override fun isUntransformed(macroCall: GrMethodCall, element: PsiElement): Boolean {
    val tree = getParsedGinqTree(macroCall) ?: return false
    val localRoots = tree.select.projections.flatMapTo(HashSet()) { projection -> projection.windows.map { it.overKw.parent.parent } }
    for (parent in element.parents(true)) {
      if (parent.isGinqRoot() || localRoots.contains(parent)) {
        return false
      }
      if (parent.isGinqUntransformed()) {
        return true
      }
    }
    return false
  }

  override fun processResolve(macroCall: GrMethodCall,
                              processor: PsiScopeProcessor,
                              state: ResolveState,
                              place: PsiElement): Boolean {
    val name = ResolveUtil.getNameHint(processor) ?: return true
    val topTree = getParsedGinqTree(macroCall) ?: return true
    val tree = place.ginqParents(macroCall, topTree).first()
    if (name == "distinct" && processor.shouldProcessMethods() && tree.select.distinct == place) {
      val call = GrLightMethodBuilder(macroCall.manager, "distinct")
      val method = JavaPsiFacade.getInstance(macroCall.project)
        .findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE, macroCall.resolveScope)
        ?.findMethodsByName("distinct", false)?.singleOrNull()
      if (method != null) {
        call.navigationElement = method
      }
      val resultTypeCollector = LinkedHashMap<String, Lazy<PsiType>>()
      val typeParameters = mutableListOf<PsiTypeParameter>()
      for ((i, arg) in tree.select.projections.withIndex()) {
        val typeParameter = call.addTypeParameter("T$i")
        val typeParameterType = typeParameter.type()
        call.addParameter("expr$i", typeParameterType)
        if (arg.alias != null) {
          typeParameters.add(typeParameter)
          resultTypeCollector[arg.alias.text] = lazy(LazyThreadSafetyMode.NONE) { typeParameterType }
        }
      }
      val clazz = JavaPsiFacade.getInstance(macroCall.project).findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_NAMED_RECORD,
                                                                         macroCall.resolveScope)
      call.returnType = clazz?.let {
        GrSyntheticNamedRecordClass(typeParameters, resultTypeCollector, resultTypeCollector.keys.toList(), it).type()
      }

      return processor.execute(call, state)
    }
    if (processor.shouldProcessMethods()) {
      val aggregate = resolveToAggregateFunction(place, name)
                      ?: resolveInOverClause(place, name)
                      ?: resolveToExists(place)
      if (aggregate != null) {
        return processor.execute(aggregate, state)
      }
    }
    if (processor.shouldProcessProperties() || processor.shouldProcessFields()) {
      val implicitVariable = resolveToCustomMember(place, name, tree)
      if (implicitVariable != null) {
        return processor.execute(implicitVariable, state)
      }
    }
    return true
  }

  private fun resolveToExists(place: PsiElement): PsiMethod? {
    val facade = JavaPsiFacade.getInstance(place.project)
    if (place is GrMethodCall && place.invokedExpression.castSafelyTo<GrReferenceExpression>()?.referenceName == "exists") {
      val method = GrLightMethodBuilder(place.manager, "exists")
      method.setReturnType(CommonClassNames.JAVA_LANG_BOOLEAN, place.resolveScope)
      val navigationResult = facade.findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE, place.resolveScope)?.findMethodsByName("exists", false)?.singleOrNull()
      if (navigationResult != null) {
        method.navigationElement = navigationResult
      }
      return method
    }
    return null
  }

  private fun resolveToCustomMember(place: PsiElement, name: String, tree: GinqExpression): GrLightVariable? {
    val facade = JavaPsiFacade.getInstance(place.project)
    if (name == "_g") {
      val containerClass = facade.findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE, place.resolveScope) ?: return null
      val clazz = facade.findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_NAMED_RECORD, place.resolveScope)
      val dataSourceTypes = tree.getDataSourceFragments().mapNotNull {
        val aliasName = it.alias.referenceName ?: return@mapNotNull null
        aliasName to lazyPub { inferDataSourceComponentType(it.dataSource.type) ?: PsiType.NULL }
      }.toMap()
      val type = clazz?.let { GrSyntheticNamedRecordClass(emptyList(), dataSourceTypes, emptyList(), it).type() } ?: return null
      val resultType = facade.elementFactory.createType(containerClass, type)
      return GrLightVariable(place.manager, "_g", resultType, containerClass)
    }
    if (name == "_rn") {
      val containerClass = facade.findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE, place.resolveScope) ?: return null
      return GrLightVariable(place.manager, "_rn", CommonClassNames.JAVA_LANG_LONG, containerClass)
    }
    return null
  }

  override fun computeCompletionVariants(macroCall: GrMethodCall, parameters: CompletionParameters, result: CompletionResultSet) {
    val position = parameters.position
    val topTree = getParsedGinqTree(macroCall)
    val closestGinq = topTree?.let { position.ginqParents(macroCall, it) }?.firstOrNull()
    if (closestGinq == null) {
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
        result.addElement(LookupElementBuilder.create("from").bold().withInsertHandler(dataSourceInsertHandler))
      }
      if (!hasSelect) {
        result.addElement(LookupElementBuilder.create("select ").bold())
      }
      return
    }
    val offset = parameters.offset
    if (this.isUntransformed(macroCall, position)) {
      if (position.parent?.parent is GrParenthesizedExpression) {
        result.addElement(LookupElementBuilder.create("from").bold().withInsertHandler(dataSourceInsertHandler))
      }
      val bindings = closestGinq.getDataSourceFragments().map { it.alias }.filter { it.endOffset < offset }
      for (binding in bindings) {
        val name = binding.referenceName ?: continue
        result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(name).withPsiElement(binding).withTypeText(binding.type?.presentableText).withIcon(JetgroovyIcons.Groovy.Variable), 1.0))
      }
      if (closestGinq.select.projections.any { PsiTreeUtil.isAncestor(it.aggregatedExpression, position, false) }) {
        for ((windowName, signature) in windowFunctions) {
          val lookupElement = LookupElementBuilder.create(windowName)
            .withIcon(JetgroovyIcons.Groovy.Method)
            .withTypeText(signature.returnType.substringAfterLast('.'))
            .withTailText(signature.parameters.joinToString(", ", "(", ")") { it.second.substringAfterLast('.') })
            .withInsertHandler(windowInsertHandler)
          result.addElement(lookupElement)
        }
      }
      return
    }
    else {
      result.stopHere()
    }
    val latestGinq = closestGinq.getQueryFragments().minByOrNull {
      val endOffset = it.keyword.endOffset
      if (endOffset <= offset) {
        offset - endOffset
      }
      else {
        Int.MAX_VALUE
      }
    }
    if (latestGinq != null) {
      val joinStartCondition: (GinqQueryFragment) -> Boolean = {
        it is GinqFromFragment || it is GinqOnFragment || (it is GinqJoinFragment && it.keyword.text == "crossjoin")
      }
      if (joinStartCondition(latestGinq)) {
        // todo: new binding name should be inferred
        joins.forEach { result.addElement(LookupElementBuilder.create(it).bold().withInsertHandler(dataSourceInsertHandler)) }
      }
      if (latestGinq is GinqJoinFragment && latestGinq.onCondition == null && latestGinq.keyword.text != "crossjoin") {
        result.addElement(LookupElementBuilder.create("on ").bold())
      }
      if (joinStartCondition(latestGinq) && closestGinq.where == null) {
        result.addElement(LookupElementBuilder.create("where ").bold())
      }
      val groupByCondition: (GinqQueryFragment) -> Boolean = { joinStartCondition(it) || it is GinqWhereFragment }
      if (groupByCondition(latestGinq) && closestGinq.groupBy == null) {
        result.addElement(LookupElementBuilder.create("groupby ").bold())
      }
      if (latestGinq is GinqGroupByFragment && latestGinq.having == null) {
        result.addElement(LookupElementBuilder.create("having ").bold())
      }
      val orderByCondition: (GinqQueryFragment) -> Boolean = {
        groupByCondition(it) || it is GinqGroupByFragment || it is GinqHavingFragment
      }
      if (orderByCondition(latestGinq) && closestGinq.orderBy == null) {
        result.addElement(LookupElementBuilder.create("orderby ").bold())
      }
      if ((orderByCondition(latestGinq) || latestGinq is GinqOrderByFragment) && closestGinq.limit == null) {
        result.addElement(LookupElementBuilder.create("limit ").bold())
      }
    }
    val overRoots = closestGinq.select.projections.flatMap { partition ->
      partition.windows
    }
    val overRoot = overRoots.find { PsiTreeUtil.isAncestor(it.overKw.parent.parent.castSafelyTo<GrMethodCall>()?.argumentList, position, false) }
    if (overRoot != null) {
      if (overRoot.partitionKw == null) {
        result.addElement(LookupElementBuilder.create("partitionby ").bold())
      }
      if (overRoot.orderBy?.keyword == null) {
        result.addElement(LookupElementBuilder.create("orderby ").bold())
      }
      if (overRoot.rowsOrRangeKw == null) {
        result.addElement(LookupElementBuilder.create("rows ").bold())
        result.addElement(LookupElementBuilder.create("range ").bold())
      }
    }
    return
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

  override fun computeFormattingBlock(macroCall: GrMethodCall, node: ASTNode, context: FormattingContext, generator: GroovyBlockGenerator): Block {
    if (node !is GrClosableBlock) {
      return super.computeFormattingBlock(macroCall, node, context, generator)
    }
    val topTree = getParsedGinqTree(macroCall) ?: return super.computeFormattingBlock(macroCall, node, context, generator)
    // todo: closest tree
    //val closestTree = node.psi.ginqParents(macroCall, topTree).firstOrNull() ?: return super.computeFormattingBlock(macroCall, node, context)
    val topBlocks = listOf(topTree.from) + topTree.joins + listOfNotNull(topTree.where, topTree.orderBy, topTree.groupBy, topTree.limit, topTree.select)
    val subBlocks = topBlocks.map { GinqFragmentBlock(it, context) }
    val children = GroovyBlockGenerator.visibleChildren(node)
    val remainingSubblocks = generator.generateCodeSubBlocks(children.filter { child -> subBlocks.all { !it.textRange.intersects(child.textRange) } })
    val allBlocks = (remainingSubblocks + subBlocks).sortedBy { it.textRange.startOffset }
    return SyntheticGroovyBlock(allBlocks, Wrap.createWrap(WrapType.NONE, false), Indent.getContinuationIndent(), Indent.getContinuationIndent(), context)
  }

  override fun computeStaticReference(macroCall: GrMethodCall, element: PsiElement): ElementResolveResult<PsiElement>? {
    val tree = getParsedGinqTree(macroCall) ?: return null
    val referenceName = element.castSafelyTo<GrReferenceElement<*>>()?.referenceName ?: return null
    val hierarchy = element.ginqParents(macroCall, tree)
    for (ginq in hierarchy) {
      val bindings = ginq.joins.map { it.alias } + listOf(ginq.from.alias) + (ginq.groupBy?.classifiers?.mapNotNull(
        AliasedExpression::alias) ?: emptyList())
      val binding = bindings.find { it.text == referenceName }
      if (binding != null) {
        return ElementResolveResult(binding)
      }
    }
    return null
  }

}
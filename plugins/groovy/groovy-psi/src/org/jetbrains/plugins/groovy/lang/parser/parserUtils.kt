// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GroovyParserUtils")
@file:Suppress("UNUSED_PARAMETER", "LiftReturnOrAssignment")

package org.jetbrains.plugins.groovy.lang.parser

import com.intellij.codeInsight.completion.CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilder.Marker
import com.intellij.lang.PsiBuilderUtil.parseBlockLazy
import com.intellij.lang.parser.GeneratedParserUtilBase.*
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.KeyWithDefaultValue
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer
import org.jetbrains.plugins.groovy.lang.parser.GroovyGeneratedParser.closure_header_with_arrow
import org.jetbrains.plugins.groovy.lang.parser.GroovyGeneratedParser.lambda_expression_head
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*
import org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets.*
import org.jetbrains.plugins.groovy.util.get
import org.jetbrains.plugins.groovy.util.set
import org.jetbrains.plugins.groovy.util.withKey
import java.util.*

private val PsiBuilder.groovyParser: GroovyParser get() = (this as Builder).parser as GroovyParser

private val collapseHook = Hook { _, marker: Marker?, elementType: IElementType ->
  marker ?: return@Hook null
  val newMarker = marker.precede()
  marker.drop()
  newMarker.collapse(elementType)
  newMarker
}

fun parseBlockLazy(builder: PsiBuilder, level: Int, deepParser: Parser, elementType: IElementType): Boolean {
  return if (builder.groovyParser.parseDeep()) {
    deepParser.parse(builder, level + 1)
  }
  else {
    val contextAwareElementType = induceContext(elementType, builder)
    register_hook_(builder, collapseHook, contextAwareElementType)
    parseBlockLazy(builder, T_LBRACE, T_RBRACE, contextAwareElementType) != null
  }
}

private fun induceContext(elementType: IElementType, builder: PsiBuilder) : IElementType = if (builder[insideSwitchExpression]) {
  when(elementType) {
    OPEN_BLOCK -> OPEN_BLOCK_SWITCH_AWARE
    BLOCK_LAMBDA_BODY -> BLOCK_LAMBDA_BODY_SWITCH_AWARE
    CLOSURE -> CLOSURE_SWITCH_AWARE
    else -> elementType
  }
} else {
  elementType
}

fun extendedStatement(builder: PsiBuilder, level: Int): Boolean = builder.groovyParser.parseExtendedStatement(builder)

fun extendedSeparator(builder: PsiBuilder, level: Int): Boolean = builder.advanceIf { builder.groovyParser.isExtendedSeparator(tokenType) }

private val currentClassNames: Key<Deque<String>> = KeyWithDefaultValue.create("groovy.parse.class.name") { LinkedList() }
private val parseDiamonds: Key<Boolean> = Key.create("groovy.parse.diamonds")
private val parseArguments: Key<Boolean> = Key.create("groovy.parse.arguments")
private val parseApplicationArguments: Key<Boolean> = Key.create("groovy.parse.application.arguments")
private val parseAnyTypeElement: Key<Boolean> = Key.create("groovy.parse.any.type.element")
private val parseQualifiedName: Key<Boolean> = Key.create("groovy.parse.qualified.name")
private val parseCapitalizedCodeReference: Key<Boolean> = Key.create("groovy.parse.capitalized")
private val parseDefinitelyTypeElement: Key<Boolean> = Key.create("groovy.parse.definitely.type.element")
private val referenceWasCapitalized: Key<Boolean> = Key.create("groovy.parse.ref.was.capitalized")
private val typeWasPrimitive: Key<Boolean> = Key.create("groovy.parse.type.was.primitive")
private val referenceHadTypeArguments: Key<Boolean> = Key.create("groovy.parse.ref.had.type.arguments")
private val referenceWasQualified: Key<Boolean> = Key.create("groovy.parse.ref.was.qualified")
private val parseClosureParameter: Key<Boolean> = Key.create("groovy.parse.closure.parameter")
private val parseNlBeforeClosureArgument: Key<Boolean> = Key.create("groovy.parse.nl.before.closure.argument")
private val insideParentheses: Key<Boolean> = Key.create("groovy.parse.inside.parentheses")
private val insideSwitchExpression: Key<Boolean> = Key.create("groovy.parse.inside.switch.expression")
private val forbidLambdaExpression: Key<Boolean> = Key.create("groovy.parse.defer.lambda.expressions")
private val compactConstructors: Key<Boolean> = Key.create("groovy.parse.contracted.constructors")

fun classIdentifier(builder: PsiBuilder, level: Int): Boolean {
  if (builder.tokenType === IDENTIFIER || builder.tokenType === KW_RECORD) {
    builder[currentClassNames]!!.push(builder.tokenText)
    builder.advanceLexer()
    return true
  }
  else {
    return false
  }
}

fun popClassIdentifier(builder: PsiBuilder, level: Int): Boolean {
  builder[currentClassNames]!!.pop()
  return true
}

fun constructorIdentifier(builder: PsiBuilder, level: Int): Boolean {
  return builder.advanceIf {
    tokenType === IDENTIFIER && tokenText == this[currentClassNames]!!.peek()
  }
}

fun allowDiamond(builder: PsiBuilder, level: Int, parser: Parser): Boolean {
  return builder.withKey(parseDiamonds, true) {
    parser.parse(builder, level)
  }
}

fun isDiamondAllowed(builder: PsiBuilder, level: Int): Boolean = builder[parseDiamonds]

fun anyTypeElement(builder: PsiBuilder, level: Int, typeElement: Parser): Boolean {
  return builder.withKey(parseAnyTypeElement, true) {
    typeElement.parse(builder, level + 1)
  }
}

private val PsiBuilder.anyTypeElementParsing get() = this[parseAnyTypeElement]

fun qualifiedName(builder: PsiBuilder, level: Int, parser: Parser): Boolean {
  return builder.withKey(parseQualifiedName, true) {
    parser.parse(builder, level)
  }
}

fun isQualifiedName(builder: PsiBuilder, level: Int): Boolean = builder[parseQualifiedName]

fun capitalizedTypeElement(builder: PsiBuilder, level: Int, typeElement: Parser, check: Parser): Boolean {
  try {
    return builder.withKey(parseCapitalizedCodeReference, true) {
      typeElement.parse(builder, level) && check.parse(builder, level)
    }
  }
  finally {
    builder[referenceWasCapitalized] = null
  }
}

private val PsiBuilder.capitalizedReferenceParsing get() = this[parseCapitalizedCodeReference] && !anyTypeElementParsing

fun refWasCapitalized(builder: PsiBuilder, level: Int): Boolean = builder[referenceWasCapitalized]

fun codeReferenceIdentifier(builder: PsiBuilder, level: Int, identifier: Parser): Boolean {
  if (builder.capitalizedReferenceParsing) {
    val capitalized = builder.isNextTokenCapitalized()
    val result = identifier.parse(builder, level)
    if (result) {
      builder[referenceWasCapitalized] = capitalized
    }
    else {
      builder[referenceWasCapitalized] = null
    }
    return result
  }
  return identifier.parse(builder, level)
}

private fun PsiBuilder.isNextTokenCapitalized(): Boolean {
  val text = tokenText
  return !text.isNullOrEmpty() && text != DUMMY_IDENTIFIER_TRIMMED && text.first().isUpperCase()
}

fun definitelyTypeElement(builder: PsiBuilder, level: Int, typeElement: Parser, check: Parser): Boolean {
  val result = builder.withKey(parseDefinitelyTypeElement, true) {
    typeElement.parse(builder, level)
  } && (builder.wasDefinitelyTypeElement() || check.parse(builder, level))
  builder.clearTypeInfo()
  return result
}

private val PsiBuilder.definitelyTypeElementParsing get() = this[parseDefinitelyTypeElement] && !anyTypeElementParsing

private fun PsiBuilder.wasDefinitelyTypeElement(): Boolean {
  return this[typeWasPrimitive] || this[referenceHadTypeArguments] || this[referenceWasQualified]
}

private fun PsiBuilder.clearTypeInfo() {
  this[typeWasPrimitive] = null
  this[referenceHadTypeArguments] = null
  this[referenceWasQualified] = null
}

fun setTypeWasPrimitive(builder: PsiBuilder, level: Int): Boolean {
  if (builder.definitelyTypeElementParsing) {
    builder[typeWasPrimitive] = true
  }
  return true
}

fun setRefWasQualified(builder: PsiBuilder, level: Int): Boolean {
  if (builder.definitelyTypeElementParsing) {
    builder[referenceWasQualified] = true
  }
  return true
}

fun setRefHadTypeArguments(builder: PsiBuilder, level: Int): Boolean {
  if (builder.definitelyTypeElementParsing) {
    builder[referenceHadTypeArguments] = true
  }
  return true
}

fun closureParameter(builder: PsiBuilder, level: Int, parameterParser: Parser): Boolean {
  return builder.withKey(parseClosureParameter, true) {
    parameterParser.parse(builder, level)
  }
}

fun isClosureParameter(builder: PsiBuilder, level: Int): Boolean = builder[parseClosureParameter]

fun enableNlBeforeClosure(builder: PsiBuilder, level: Int, parameterParser: Parser): Boolean {
  return builder.withKey(parseNlBeforeClosureArgument, true) {
    parameterParser.parse(builder, level)
  }
}

fun disableNlBeforeClosure(builder: PsiBuilder, level: Int): Boolean {
  builder[parseNlBeforeClosureArgument] = false
  return true
}

fun callTail(builder: PsiBuilder, level: Int, withNl: Parser, withoutNl: Parser): Boolean {
  if (builder.latestDoneMarker?.tokenType == NEW_EXPRESSION || builder[parseNlBeforeClosureArgument]) {
    return withNl.parse(builder, level)
  }
  else {
    return withoutNl.parse(builder, level)
  }
}

fun parseArgument(builder: PsiBuilder, level: Int, argumentParser: Parser): Boolean {
  return builder.withKey(parseArguments, true) {
    argumentParser.parse(builder, level)
  }
}

fun isArguments(builder: PsiBuilder, level: Int): Boolean = builder[parseArguments]

fun applicationArguments(builder: PsiBuilder, level: Int, parser: Parser): Boolean {
  return builder.withKey(parseApplicationArguments, true) {
    parser.parse(builder, level)
  }
}

fun notApplicationArguments(builder: PsiBuilder, level: Int, parser: Parser): Boolean {
  return builder.withKey(parseApplicationArguments, null) {
    parser.parse(builder, level)
  }
}

fun isApplicationArguments(builder: PsiBuilder, level: Int): Boolean = builder[parseApplicationArguments]

fun closureArgumentSeparator(builder: PsiBuilder, level: Int, closureArguments: Parser): Boolean {
  if (builder.tokenType === NL) {
    if (isApplicationArguments(builder, level)) {
      return false
    }
    builder.advanceLexer()
  }
  return closureArguments.parse(builder, level)
}

/**
 *```
 * foo a                    // ref <- application
 * foo a ref                // application <- ref
 * foo a(ref)
 * foo a.ref
 * foo a[ref]
 * foo a ref c              // ref <- application
 * foo a ref(c)             // ref <- call
 * foo a ref[c]             // ref <- index
 * foo a ref[c] ref         // index <- ref
 * foo a ref[c] (a)         // index <- call
 * foo a ref[c] {}          // index <- call
 * foo a ref(c) ref         // call <- ref
 * foo a ref(c)(c)          // call <- call
 * foo a ref(c)[c]          // call <- index
 *```
 */
fun parseApplication(builder: PsiBuilder, level: Int,
                     refParser: Parser,
                     applicationParser: Parser,
                     callParser: Parser,
                     indexParser: Parser): Boolean {
  val wrappee = builder.latestDoneMarker ?: return false
  val nextLevel = level + 1
  return when (wrappee.tokenType) {
    APPLICATION_EXPRESSION -> {
      refParser.parse(builder, nextLevel)
    }
    METHOD_CALL_EXPRESSION -> {
      indexParser.parse(builder, nextLevel) ||
      callParser.parse(builder, nextLevel) ||
      refParser.parse(builder, nextLevel)
    }
    REFERENCE_EXPRESSION -> {
      indexParser.parse(builder, nextLevel) ||
      callParser.parse(builder, nextLevel) ||
      applicationParser.parse(builder, nextLevel)
    }
    APPLICATION_INDEX -> {
      callParser.parse(builder, nextLevel) ||
      refParser.parse(builder, nextLevel)
    }
    INSTANCEOF_EXPRESSION -> false
    else -> applicationParser.parse(builder, nextLevel)
  }
}

fun parseKeyword(builder: PsiBuilder, level: Int): Boolean = builder.advanceIf(KEYWORDS)

fun parseReservedKeyword(builder: PsiBuilder, level: Int): Boolean = builder.advanceIf(RESERVED_KEYWORDS)

fun parsePrimitiveType(builder: PsiBuilder, level: Int): Boolean = builder.advanceIf(primitiveTypes)

fun assignmentOperator(builder: PsiBuilder, level: Int): Boolean = builder.advanceIf(ASSIGNMENTS)

fun equalityOperator(builder: PsiBuilder, level: Int): Boolean = builder.advanceIf(EQUALITY_OPERATORS)

fun error(builder: PsiBuilder, level: Int, @PropertyKey(resourceBundle = GroovyBundle.BUNDLE) key: String): Boolean {
  val marker = builder.latestDoneMarker ?: return false
  val elementType = marker.tokenType
  val newMarker = (marker as Marker).precede()
  marker.drop()
  builder.error(GroovyBundle.message(key))
  newMarker.done(elementType)
  return true
}

fun unexpected(builder: PsiBuilder, level: Int, @PropertyKey(resourceBundle = GroovyBundle.BUNDLE) key: String): Boolean {
  return unexpected(builder, level, Parser { b, _ -> b.any() }, key)
}

fun unexpected(builder: PsiBuilder, level: Int, parser: Parser, @PropertyKey(resourceBundle = GroovyBundle.BUNDLE) key: String): Boolean {
  val marker = builder.mark()
  if (parser.parse(builder, level)) {
    marker.error(GroovyBundle.message(key))
  }
  else {
    marker.drop()
  }
  return true
}

fun parseTailLeftFlat(builder: PsiBuilder, level: Int, head: Parser, tail: Parser): Boolean {
  val marker = builder.mark()
  if (!head.parse(builder, level)) {
    marker.drop()
    return false
  }
  else {
    if (!tail.parse(builder, level)) {
      marker.drop()
      report_error_(builder, false)
    }
    else {
      val tailMarker = builder.latestDoneMarker!!
      val elementType = tailMarker.tokenType
      (tailMarker as Marker).drop()
      marker.done(elementType)
    }
    return true
  }
}

private fun <T> PsiBuilder.lookahead(action: PsiBuilder.() -> T): T {
  val marker = mark()
  val result = action()
  marker.rollbackTo()
  return result
}

private fun PsiBuilder.any(): Boolean = advanceIf { true }

private fun PsiBuilder.advanceIf(tokenSet: TokenSet): Boolean = advanceIf { tokenType in tokenSet }

private inline fun PsiBuilder.advanceIf(crossinline condition: PsiBuilder.() -> Boolean): Boolean {
  if (condition()) {
    advanceLexer()
    return true
  }
  else {
    return false
  }
}

fun noMatch(builder: PsiBuilder, level: Int): Boolean = false

fun addVariant(builder: PsiBuilder, level: Int, variant: String): Boolean {
  addVariant(builder, "<$variant>")
  return true
}

fun clearVariants(builder: PsiBuilder, level: Int): Boolean {
  val state = builder.state
  state.clearVariants(state.currentFrame)
  return true
}

fun replaceVariants(builder: PsiBuilder, level: Int, variant: String): Boolean {
  return clearVariants(builder, level) && addVariant(builder, level, variant)
}

fun clearError(builder: PsiBuilder, level: Int): Boolean {
  builder.state.currentFrame.errorReportedAt = -1
  return true
}

fun withProtectedLastVariantPos(builder: PsiBuilder, level: Int, parser: Parser): Boolean {
  val state = builder.state
  val prev = state.currentFrame.lastVariantAt
  if (parser.parse(builder, level)) {
    return true
  }
  else {
    state.currentFrame.lastVariantAt = prev
    return false
  }
}

private val PsiBuilder.state: ErrorState get() = ErrorState.get(this)

fun castOperandCheck(builder: PsiBuilder, level: Int): Boolean {
  return builder.tokenType !== T_LPAREN || builder.lookahead {
    castOperandCheckInner(this)
  }
}

private fun castOperandCheckInner(builder: PsiBuilder): Boolean {
  var parenCount = 0
  while (!builder.eof()) {
    builder.advanceLexer()
    val tokenType = builder.tokenType
    when {
      tokenType === T_LPAREN -> {
        parenCount++
      }
      tokenType === T_RPAREN -> {
        if (parenCount == 0) {
          // we discovered closing parenthesis and didn't find any commas
          return true
        }
        parenCount--
      }
      tokenType === T_COMMA -> {
        if (parenCount == 0) {
          // comma on the same level of parentheses means we are in argument list
          return false
        }
      }
    }
  }
  return false
}

fun isAfterClosure(builder: PsiBuilder, level: Int): Boolean {
  return builder.latestDoneMarker?.tokenType is GrClosureElementType
}

fun isParameterizedClosure(builder: PsiBuilder, level: Int): Boolean {
  return builder.lookahead {
    isParameterizedClosureInner(this, level)
  }
}

private fun isParameterizedClosureInner(builder: PsiBuilder, level: Int): Boolean {
  if (!consumeTokenFast(builder, T_LBRACE)) return false
  GroovyGeneratedParser.mb_nl(builder, level)
  return closure_header_with_arrow(builder, level)
}

fun isParameterizedLambda(builder: PsiBuilder, level: Int): Boolean {
  return builder.lookahead {
    lambda_expression_head(builder, level)
  }
}

private val explicitLeftMarker = Key.create<Marker>("groovy.parse.left.marker")

/**
 * Stores [PsiBuilder.getLatestDoneMarker] in user data to be able to use it later in [wrapLeft].
 */
fun markLeft(builder: PsiBuilder, level: Int): Boolean {
  builder[explicitLeftMarker] = builder.latestDoneMarker as? Marker
  return true
}

/**
 * Let sequence `a b c d` result in the following tree: `(a) (b) (c) (d)`.
 * Then `a b <<markLeft>> c d <<wrapLeft>>` will result in: `(a) ((b) (c) d)`
 */
fun wrapLeft(builder: PsiBuilder, level: Int): Boolean {
  val explicitLeft = builder[explicitLeftMarker] ?: return false
  val latest = builder.latestDoneMarker ?: return false
  explicitLeft.precede().done(latest.tokenType)
  (latest as? Marker)?.drop()
  return true
}

fun forceWrapLeft(builder: PsiBuilder, level: Int, parser: Parser): Boolean {
  val marker = builder.latestDoneMarker as? Marker ?: return false
  val r = parser.parse(builder, level)
  if (!r) return false
  val latest = builder.latestDoneMarker ?: return false
  marker.precede().done(latest.tokenType)
  (latest as? Marker)?.drop()
  return true
}

fun choice(builder: PsiBuilder, level: Int, vararg parsers: Parser): Boolean {
  assert(parsers.size > 1)
  for (parser in parsers) {
    if (parser.parse(builder, level)) return true
  }
  return false
}

fun isBlockParseable(text: CharSequence): Boolean {
  val lexer = GroovyLexer().apply {
    start(text)
  }
  if (lexer.tokenType !== T_LBRACE) return false
  lexer.advance()

  val leftStack = LinkedList<IElementType>().apply {
    push(T_LBRACE)
  }

  while (true) {
    ProgressManager.checkCanceled()
    val type = lexer.tokenType ?: return leftStack.isEmpty()
    if (leftStack.isEmpty()) {
      return false
    }
    when (type) {
      T_LBRACE,
      T_LPAREN -> leftStack.push(type)
      T_RBRACE -> {
        if (leftStack.isEmpty() || leftStack.pop() != T_LBRACE) {
          return false
        }
      }
      T_RPAREN -> {
        if (leftStack.isEmpty() || leftStack.pop() != T_LPAREN) {
          return false
        }
      }
    }
    lexer.advance()
  }
}

fun insideParentheses(builder: PsiBuilder, level: Int, parser: Parser): Boolean {
  // enables 'yield' keyword in this and all inner code blocks
  return builder.withKey(insideParentheses, true) {
    parser.parse(builder, level)
  }
}

fun insideParentheses(builder: PsiBuilder, level: Int): Boolean = builder[insideParentheses]

fun insideSwitchExpression(builder: PsiBuilder, level: Int, parser: Parser): Boolean = builder.withKey(insideSwitchExpression, true) {
  parser.parse(builder, level)
}

fun insideSwitchExpression(builder: PsiBuilder, level: Int): Boolean = builder[insideSwitchExpression]

/**
 * Suppose we have a case section 'case a -> 10' somewhere inside the switch expression.
 * Then, as an expression list is expected after 'case', the token set 'a -> 10' is recognized as a lambda expression.
 * This function forbid the choice of lambda expression, making unqualified reference an only choice
 */
fun forbidLambdaExpressions(builder: PsiBuilder, level: Int, parser : Parser): Boolean {
  return builder.withKey(forbidLambdaExpression, true) {
    parser.parse(builder, level)
  }
}

fun isLambdaExpressionAllowed(builder : PsiBuilder, level: Int) : Boolean {
  return !builder[forbidLambdaExpression]
}

fun enableCompactConstructors(builder : PsiBuilder, level : Int, parser : Parser) : Boolean {
  return builder.withKey(compactConstructors, true) {
    parser.parse(builder, level)
  }
}

fun disableCompactConstructors(builder : PsiBuilder, level : Int, parser : Parser) : Boolean {
  return builder.withKey(compactConstructors, false) {
    parser.parse(builder, level)
  }
}

fun isCompactConstructorAllowed(builder: PsiBuilder, level : Int) : Boolean {
  return builder[compactConstructors]
}
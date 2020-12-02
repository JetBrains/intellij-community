/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser.kotlin

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.iStr
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.*
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.FILE_CONSTRUCTOR_NAME
import com.android.tools.idea.gradle.dsl.model.notifications.NotificationTypeReference.INCOMPLETE_PARSING
import com.android.tools.idea.gradle.dsl.model.notifications.NotificationTypeReference.INVALID_EXPRESSION
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement
import com.android.tools.idea.gradle.dsl.parser.elements.*
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.getPropertiesElement
import com.google.common.collect.Lists
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil.unquoteString
import com.intellij.psi.PsiElement
import com.intellij.util.IncorrectOperationException
import com.intellij.util.text.LiteralFormatUtil
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.parsing.parseBoolean
import org.jetbrains.kotlin.parsing.parseNumericLiteral
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import java.math.BigDecimal

/**
 * Parser for .gradle.kt files. This method produces a [GradleDslElement] tree.
 */
class KotlinDslParser(val psiFile : KtFile, val dslFile : GradleDslFile): KtVisitor<Unit, GradlePropertiesDslElement>(), KotlinDslNameConverter, GradleDslParser {

  //
  // Methods for GradleDslParser
  //
  override fun parse() {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    psiFile.script?.blockExpression?.statements?.map {
      if (it is KtScriptInitializer) it.body else it
    }?.requireNoNulls()?.forEach {
      it.accept(this, dslFile)
    }
  }

  override fun convertToPsiElement(context: GradleDslSimpleExpression, literal: Any): PsiElement? {
    return try {
      createLiteral(context, dslFile, literal)
    }
    catch (e : IncorrectOperationException) {
      dslFile.context.getNotificationForType(dslFile, INVALID_EXPRESSION).addError(e)
      null
    }
  }

  override fun setUpForNewValue(context: GradleDslLiteral, newValue: PsiElement?) {
    val newPsiElement = when (newValue) {
      is KtBinaryExpressionWithTypeRHS -> newValue.left
      else -> newValue
    }
    val isReference = newPsiElement is KtNameReferenceExpression || newPsiElement is KtDotQualifiedExpression ||
                      newPsiElement is KtClassLiteralExpression || newPsiElement is KtArrayAccessExpression
    context.isReference = isReference
  }

  override fun extractValue(context: GradleDslSimpleExpression, literal: PsiElement, resolve: Boolean): Any? {
    when (literal) {
      // Ex: KotlinCompilerVersion, android.compileSdkVersion
      is KtNameReferenceExpression, is KtDotQualifiedExpression -> {
        if (resolve) {
          val gradleDslElement = context.resolveExternalSyntaxReference(literal.text, true)
          // Only get the value if the element is a GradleDslSimpleExpression.
          if (gradleDslElement is GradleDslSimpleExpression) {
            return gradleDslElement.value
          }
        }
        return unquoteString(literal.text)
      }
      // prop[0], rootProject.extra["kotlin_version"]
      is KtArrayAccessExpression -> {
        if (resolve) {
          val gradleDslElement = context.resolveExternalSyntaxReference(literal.text, true)
          if (gradleDslElement is GradleDslSimpleExpression) {
            return gradleDslElement.value
          }
        }
        return unquoteString(literal.text)
      }
      // For String and constant literals. Ex : Integers, single-quoted Strings.
      is KtStringTemplateExpression -> {
        if (!resolve || context.hasCycle()) {
          return unquoteString(literal.text)
        }
        val injections = context.resolvedVariables
        return GradleReferenceInjection.injectAll(literal, injections)
      }
      is KtConstantExpression -> {
        return when (literal.node.elementType) {
          KtNodeTypes.INTEGER_CONSTANT-> {
            val numericalValue = parseNumericLiteral(literal.text, literal.node.elementType)
            if (numericalValue is Long && (numericalValue > Integer.MAX_VALUE || numericalValue < Integer.MIN_VALUE)) return numericalValue
            return (numericalValue as Number).toInt()
          }
          KtNodeTypes.FLOAT_CONSTANT -> {
            // FLOAT_CONSTANT applies for float, double, and bigDecimal values and we use bigDecimal to ensure the best precision.
            // For values with "f" suffix, it's safe to remove the suffix here as we can get it from the psi element text value for the writer.
            try {
              return BigDecimal(LiteralFormatUtil.removeUnderscores(literal.text).trimEnd('f', 'F'))
            } catch (e: NumberFormatException) {
              Logger.getInstance(KotlinDslParser::class.java).error(e)
              return null
            }
          }
          KtNodeTypes.BOOLEAN_CONSTANT -> parseBoolean(literal.text)
          else -> unquoteString(literal.text)
        }
      }
      is KtCallExpression -> {
        if (literal.name() == "kotlin") {
          val argumentList = literal.valueArgumentList ?: return literal.text
          // If the method has two arguments then it should automatically resolve to a dependency.
          if (argumentList.arguments.size == 2) {
            val moduleName = argumentList.arguments[0]?.getArgumentExpression() ?: return null
            val version = argumentList.arguments[1].getArgumentExpression() ?: return null
            // TODO(b/142530879): fix version value injection from import statements.
            return "org.jetbrains.kotlin:kotlin-${unquoteString(moduleName.text)}:${unquoteString(version.text)}"
          }
          // If the method has one argument, we should check if it's declared under a plugins block in order to resolve it to a plugin,
          // to a dependency if declared under dependencies block, or not resolve it for other cases.
          else if (argumentList.arguments.size == 1) {
            val nameExpression = argumentList.arguments[0].getArgumentExpression() ?: return null
            return when (context.parent?.fullName) {
              "plugins" -> "org.jetbrains.kotlin.${unquoteString(nameExpression.text)}"
              "dependencies" -> "org.jetbrains.kotlin:kotlin-${unquoteString(nameExpression.text)}"
              else -> KotlinDslRawText(literal.text)
            }
          }
        }
        return KotlinDslRawText(literal.text)
      }
      is KtBinaryExpressionWithTypeRHS -> return when (val expressionInfo = literal.left) {
        is KtArrayAccessExpression -> this.extractValue(context, expressionInfo, resolve)
        else -> unquoteString(literal.text)
      }
      else -> return KotlinDslRawText(literal.text)
    }
  }

  override fun convertToExcludesBlock(excludes: List<ArtifactDependencySpec>): PsiElement? {
    val factory = KtPsiFactory(dslFile.project)
    val block = factory.createBlock("")
    // Currently, createBlock returns a block with two empty lines, we should keep only one new line.
    val firstStatementOrEmpty = block.firstChild.nextSibling
    if (firstStatementOrEmpty.text == "\n\n") {
      firstStatementOrEmpty.delete()
      block.addAfter(factory.createNewLine(), block.firstChild)
    }
    excludes.forEach {
      val group = if (FakeArtifactElement.shouldInterpolate(it.group)) iStr(it.group ?: "\"\"") else "\"${it.group}\""
      val name = if (FakeArtifactElement.shouldInterpolate(it.name)) iStr(it.name) else "\"${it.name}\""
      val text = "exclude(mapOf(\"group\" to $group, \"module\" to $name))"
      val expression = factory.createExpressionIfPossible(text)
      if (expression != null) {
        block.addBefore(expression, block.lastChild)
        block.addBefore(factory.createNewLine(), block.lastChild)
      }
    }
    return block
  }

  override fun shouldInterpolate(elementToCheck: GradleDslElement): Boolean {
    return when (elementToCheck) {
      is GradleDslSettableExpression -> elementToCheck.currentElement is KtStringTemplateExpression
      is GradleDslSimpleExpression -> elementToCheck.expression is KtStringTemplateExpression
      else -> elementToCheck.psiElement is KtStringTemplateExpression
    }
  }

  override fun getResolvedInjections(context: GradleDslSimpleExpression, psiElement: PsiElement): List<GradleReferenceInjection> {
    return findInjections(context, psiElement, false)
  }

  override fun getInjections(context: GradleDslSimpleExpression, psiElement: PsiElement): List<GradleReferenceInjection> {
    return findInjections(context, psiElement, true)
  }

  override fun getPropertiesElement(nameParts: List<String>,
                                    parentElement: GradlePropertiesDslElement,
                                    nameElement: GradleNameElement?): GradlePropertiesDslElement? {
    return dslFile.getPropertiesElement(nameParts, this, parentElement, nameElement)
  }

  // Check if this is a block with a methodCall as name, and get the block in such case. Ex: getByName("release") -> the release block.
  private fun methodCallBlock(
    expression: KtCallExpression,
    parent: GradlePropertiesDslElement,
    name: GradleNameElement? = null): GradlePropertiesDslElement? {
    val blockName = methodCallBlockName(expression) ?: return null
    val propertiesElement = getPropertiesElement(listOf(blockName), parent, name) ?: return null
    if (propertiesElement is GradleDslNamedDomainElement) {
      // TODO(xof): this way of keeping track of how we got hold of the block (which method name) only works once
      propertiesElement.methodName = propertiesElement.methodName ?: expression.name()
    }
    return propertiesElement
  }

  //
  // Methods to perform the parsing on the KtFile
  //
  override fun visitCallExpression(expression: KtCallExpression, parent: GradlePropertiesDslElement) {
    // If the call expression has no name, we don't know how to handle it.
    var referenceName = expression.name() ?: return

    if (parent is DependenciesDslElement) {
      // There is an extension method on String.invoke() in KotlinScript dependencies.  (Meant to be used to add dependencies to
      // configurations which are not predefined set, but would in principle work for all configurations)
      referenceName = unquoteString(referenceName);
    }

    val referenceExpression = expression.referenceExpression()
    var name =
      if (referenceExpression != null) GradleNameElement.from(referenceExpression, this) else GradleNameElement.create(referenceName)

    // If expression is a pure block element and not an expression.
    if (expression.isBlockElement(parent)) {
      // If the block has a localMethodName, the nameElement should use the argument valueExpression psi. (ex: create("release") -> release)
      val argumentList = expression.valueArgumentList
      if (argumentList != null && argumentList.arguments.size > 0) {
        val argumentExpressionVal = argumentList.arguments[0].getArgumentExpression()
        name = if (argumentExpressionVal != null) GradleNameElement.from(argumentExpressionVal, this) else name
      }
      // We might need to apply the block to multiple DslElements.
      val blockElements = Lists.newArrayList<GradlePropertiesDslElement>()
      // If the block is allprojects, we need to apply the closure to the project and to all its subprojects.
      if (parent is GradleDslFile && referenceName == "allprojects") {
        // The block has to be applied to the project.
        blockElements.add(parent)
        // Then the block should be applied to subprojects.
        referenceName = "subprojects"
      }
      val propertiesElement = methodCallBlock(expression, parent, name) ?: getPropertiesElement(listOf(referenceName), parent, name) ?: return
      val body = expression.lambdaArguments.getOrNull(0)?.getLambdaExpression()?.bodyExpression

      propertiesElement.setPsiElement(body ?: expression)
      if (body == null && propertiesElement is GradleDslBlockElement) {
        propertiesElement.hasBraces = false
      }
      blockElements.add(propertiesElement)
      blockElements.forEach { block ->
        // Visit the children of this element, with the current block set as parent.
        expression.lambdaArguments.getOrNull(0)?.getLambdaExpression()?.bodyExpression?.statements?.requireNoNulls()?.forEach {
          it.accept(this, block)
        }
      }
    }
    else {
      // Get args and block.
      val argumentsList = expression.valueArgumentList
      val argumentsBlock = expression.lambdaArguments.getOrNull(0)?.getLambdaExpression()?.bodyExpression
      if (argumentsList != null) {
        val callExpression =
          getCallExpression(parent, expression, name, argumentsList, referenceName, true) ?: return
        if (argumentsBlock != null) {
          callExpression.setParsedClosureElement(getClosureBlock(callExpression, argumentsBlock, name))
        }

        callExpression.elementType = REGULAR
        parent.addParsedElement(callExpression)
      }

    }
  }

  override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression, parent: GradlePropertiesDslElement) {

    fun parentBlockFromReceiver(receiver: KtExpression): GradlePropertiesDslElement? {
      var current = parent
      receiver.accept(object : KtTreeVisitorVoid() {
        override fun visitReferenceExpression(expression: KtReferenceExpression) {
          when (expression) {
            is KtNameReferenceExpression -> {
              current = getPropertiesElement(listOf(expression.text), current, null) ?: return
            }
            else -> Unit
          }
        }
        override fun visitCallExpression(expression: KtCallExpression) {
          current = methodCallBlock(expression, current) ?: return
        }
      }, null)
      return current
    }

    // android.buildTypes.release { minify_enabled true }
    val receiver = expression.receiverExpression
    when (val selector = expression.selectorExpression) {
      is KtCallExpression -> {
        val parentBlock = parentBlockFromReceiver(receiver)
        if (parentBlock == null) {
          super.visitDotQualifiedExpression(expression, parent)
          return
        }
        visitCallExpression(selector, parentBlock)
      }
      else -> super.visitDotQualifiedExpression(expression, parent)
    }
  }

  private fun getDotQualifiedExpression(
    parent: GradleDslElement,
    psiElement: PsiElement,
    name: GradleNameElement,
    expression: KtDotQualifiedExpression
  ) : GradleDslExpression? {
    val receiver = expression.receiverExpression
    val selector = expression.selectorExpression
    when (selector) {
      is KtCallExpression -> {
        // Check if this is about a localMethod used for blocks referencing, or not.
        val referenceName = selector.name()
        if (isValidBlockName(referenceName)) {
          return GradleDslLiteral(parent, expression, name, expression, true)
        }
        else {
          // This is the case of method calls for which we want to keep all the expression name as reference and resolve the nested
          // method call. For example : System.getEnv("pass") -> in such case, we shouldn't consider the expression as a literal but rather
          // as a methodCall.
          val methodName = "${receiver.text}.${referenceName}"
          return getMethodCall(parent, expression, name, methodName, selector.valueArgumentList, false)
        }
      }
    }

    return getExpressionElement(parent, psiElement, name, expression)
  }

  override fun visitBinaryExpression(expression: KtBinaryExpression, parent: GradlePropertiesDslElement) {
    var parentBlock = parent
    val name: GradleNameElement
    // Check the expression is valid.
    if (expression.operationToken != KtTokens.EQ) return
    val left = expression.left ?: return
    val right = expression.right ?: return

    name = GradleNameElement.from(left, this)
    if (name.isEmpty) return
    if (name.isQualified) {
      val nestedElement = getPropertiesElement(name.qualifyingParts(), parent, null) ?: return
      parentBlock = nestedElement
    }

    val matcher = GradleNameElement.INDEX_PATTERN.matcher(name.name())
    if (matcher.find()) {
      // we have an index / dereferencing lvalue: find the actual element that we will need to modify.
      val property = modelDescriptionForParent(matcher.group(0), parentBlock)?.name ?: matcher.group(0)
      parentBlock = parentBlock.getElement(property) as? GradlePropertiesDslElement ?: return
      // we do not need to convert this to a model name because it must be a user-supplied property.
      var index = if (matcher.find()) matcher.group(1) else return
      while (matcher.find()) {
        parentBlock = GradleDslSimpleExpression.dereferencePropertiesElement(parentBlock, index) ?: return
        index = matcher.group(1)
      }
      when(parentBlock) {
        is GradleDslExpressionMap -> {
          val name = GradleNameElement.create(unquoteString(index))
          val propertyElement = createExpressionElement(parentBlock, expression, name, right, true) ?: return
          propertyElement.elementType = DERIVED

          parentBlock.setParsedElement(propertyElement)
        }
        else -> return
      }
    }
    else {
      val propertyElement = createExpressionElement(parentBlock, expression, name, right, true) ?: return
      propertyElement.setUseAssignment(true)
      propertyElement.elementType = REGULAR

      parentBlock.setParsedElement(propertyElement)
    }
  }

  override fun visitProperty(expression: KtProperty, parent: GradlePropertiesDslElement) {
    val identifier = expression.nameIdentifier ?: return
    val delegate = expression.delegate
    if (delegate == null) {
      // handling standard KtProperties ( val foo = ...) to support variables
      val initializer = expression.initializer ?: return

      // if we've got this far, we have a variable declaration/initialization of the form "val foo = bar"

      val name = GradleNameElement.from(identifier, this)
      val propertyElement = createExpressionElement(parent, expression, name, initializer, true) ?: return
      propertyElement.elementType = VARIABLE
      parent.setParsedElement(propertyElement)
    }
    else {
      // handling delegated KtProperties ( val foo by ... ) to support Gradle extra properties.
      val callExpression = delegate.expression as? KtCallExpression ?: return

      val referenceExpression = callExpression.referenceExpression() ?: return
      // TODO(xof): it's more complicated than this, of course; kotlinscript can express gradle properties on other
      //  projects' extra blocks, using "val foo by rootProject.extra(42)".  The Kotlinscript Psi tree is the wrong way round
      //  for us to detect it easily: (rootProject dot (extra [42])) rather than ((rootProject dot extra) [42]) so more work
      //  is needed here.
      if (referenceExpression.text != "extra") return
      val arguments = callExpression.valueArgumentList?.arguments ?: return
      if (arguments.size != 1) return
      val initializer = arguments[0].getArgumentExpression() ?: return

      // If we've got this far, we have an extra property declaration/initialization of the form "val foo by extra(bar)".

      val ext = getPropertiesElement(listOf("ext"), parent, null) ?: return
      val name = GradleNameElement.from(identifier, this) // TODO(xof): error checking: empty/qualified/etc
      val propertyElement = createExpressionElement(ext, expression, name, initializer, true) ?: return
      // This Property is assigning a value to a property, so we need to set the UseAssignment to true.
      propertyElement.setUseAssignment(true)
      propertyElement.elementType = REGULAR
      ext.setParsedElement(propertyElement)
    }
  }

  private fun getCallExpression(
    parentElement : GradleDslElement,
    psiElement : PsiElement,
    name : GradleNameElement,
    argumentsList : KtValueArgumentList,
    methodName : String,
    isFirstCall : Boolean,
    isLiteral : Boolean = false
  ) : GradleDslExpression? {
    when (methodName) {
      "mapOf", "mutableMapOf" -> return getExpressionMap(parentElement, psiElement, name, argumentsList.arguments, isLiteral)
      "listOf", "mutableListOf" -> return getExpressionList(parentElement, psiElement, name, argumentsList.arguments, isLiteral, false)
      "setOf", "mutableSetOf" -> return getExpressionList(parentElement, psiElement, name, argumentsList.arguments, isLiteral, true)
      "kotlin" -> return GradleDslLiteral(parentElement, psiElement, name, psiElement, false)
      FILE_CONSTRUCTOR_NAME -> return getMethodCall(parentElement, psiElement, name, FILE_CONSTRUCTOR_NAME, argumentsList, true)
    }

    val arguments = argumentsList.arguments
    // If nontrivially-all the arguments are named, return a GradleDslExpressionMap
    if (isFirstCall && arguments.size > 0 && arguments.all { it.isNamed() }) {
      return getArglistMap(parentElement, psiElement, name, argumentsList.arguments)
    }
    // If the CallExpression has one argument only that is a callExpression, we skip the current CallExpression.
    if (arguments.size != 1) {
      return getMethodCall(parentElement, psiElement, name, methodName, argumentsList, false)
    }
    else {
      val argumentExpression = arguments[0].getArgumentExpression()
      if (argumentExpression is KtCallExpression) {
        val argumentsName = (arguments[0].getArgumentExpression() as KtCallExpression).name() ?: return null
        if (isFirstCall) {
          val argumentsList = argumentExpression.valueArgumentList
          return if (argumentsList != null) getCallExpression(
            parentElement, argumentExpression, name, argumentsList, argumentsName, false) else null
        }
        return getMethodCall(
          parentElement,
          psiElement,
          // isNamed() checks if getArgumentName() is not null, so using !! here is safe (unless the implementation changes).
          if (arguments[0].isNamed()) GradleNameElement.create(arguments[0].getArgumentName()!!.text) else name,
          methodName,
          argumentsList,
          false
        )
      }
      if (isFirstCall && arguments[0].getArgumentExpression() != null && !arguments[0].isNamed()) {
        return getExpressionElement(
          parentElement,
          arguments[0],
          name,
          arguments[0].getArgumentExpression() as KtElement)
      }
      return getMethodCall(parentElement, psiElement, name, methodName, argumentsList, false)
    }
  }

  private fun getMethodCall(
    parent: GradleDslElement,
    psiElement: PsiElement,
    name: GradleNameElement,
    methodName: String,
    arguments: KtValueArgumentList?,
    isConstructor: Boolean
  ): GradleDslMethodCall {

    val methodCall = GradleDslMethodCall(parent, psiElement, name, methodName, isConstructor)
    if (arguments == null) return methodCall
    val argumentList = getExpressionList(methodCall, arguments, GradleNameElement.empty(), arguments.arguments, false)
    methodCall.setParsedArgumentList(argumentList)
    return methodCall
  }

  private fun getExpressionMap(parentElement : GradleDslElement,
                               mapPsiElement: PsiElement,
                               propertyName : GradleNameElement,
                               arguments : List<KtElement>,
                               isLiteral : Boolean) : GradleDslExpressionMap {
    val expressionMap = GradleDslExpressionMap(parentElement, mapPsiElement, propertyName, isLiteral)
    arguments.map {
      arg -> (arg as KtValueArgument).getArgumentExpression()
    }.filter {
      // Filter map elements that either have a left or right element null. This filter makes using !! safe in the next mapNotNull fun.
      expression -> expression is KtBinaryExpression && expression.operationReference.getReferencedName() == "to" &&
                    expression.left != null && expression.right != null
    }.mapNotNull {
      expression -> createExpressionElement(
      expressionMap, mapPsiElement, GradleNameElement.from((expression as KtBinaryExpression).left!!, this), expression.right!!)
    }.forEach(expressionMap::addParsedElement)

    return expressionMap
  }

  private fun getArglistMap(
    parentElement: GradleDslElement,
    arglistPsiElement: PsiElement,
    propertyName: GradleNameElement,
    arguments: List<KtValueArgument>
  ): GradleDslExpressionMap {
    val map = GradleDslExpressionMap(parentElement, arglistPsiElement, propertyName, false)
    map.asNamedArgs = true
    arguments
      .map { arg -> arg.getArgumentName() to arg.getArgumentExpression() }
      .filter { e -> e.first != null && e.second != null }
      .mapNotNull { e -> createExpressionElement(map, arglistPsiElement, GradleNameElement.from(e.first!!, this), e.second!!) }
      .forEach(map::addParsedElement)
    return map
  }

  private fun getExpressionList(parentElement : GradleDslElement,
                                listPsiElement : PsiElement,
                                propertyName : GradleNameElement,
                                valueArguments : List<KtElement>,
                                isLiteral : Boolean,
                                isSet : Boolean = false) : GradleDslExpressionList {
    val expressionList = GradleDslExpressionList(parentElement, listPsiElement, isLiteral, propertyName)
    valueArguments.map {
      expression -> expression as KtValueArgument
    }.filter {
      arg -> arg.getArgumentExpression() != null
    }.mapNotNull {
      argumentExpression -> createExpressionElement(
      expressionList,
      argumentExpression,
      // isNamed() checks if getArgumentName() is not null, so using !! here is safe (unless the implementation changes).
      if (argumentExpression.isNamed())
        GradleNameElement.create(argumentExpression.getArgumentName()!!.text) else GradleNameElement.empty(),
      argumentExpression.getArgumentExpression() as KtExpression, isLiteral)
    }.forEach {
      if (it is GradleDslClosure) {
        parentElement.setParsedClosureElement(it)
      }
      else {
        expressionList.addParsedExpression(it)
      }
    }
    return expressionList
  }

  private fun createExpressionElement(parent : GradleDslElement,
                                      psiElement : PsiElement,
                                      name: GradleNameElement,
                                      expression : KtExpression,
                                      isLiteral : Boolean = false) : GradleDslExpression? {
    // Parse all the ValueArgument types.
    when (expression) {
      is KtValueArgumentList -> return getExpressionList(parent, expression, name, expression.arguments, isLiteral)
      is KtCallExpression -> {
        // Ex: implementation(kotlin("stdlib-jdk7")).
        val expressionName = expression.name() ?: return null
        // Special handling for KtCallExpression named File assuming that it is a constructor call as it is only used for path values so far.
        // TODO (karimai): this is a workaround to avoid using psi resolution for now, See b/145395390.
        if (expressionName == FILE_CONSTRUCTOR_NAME) {
          return getMethodCall(parent, expression, name, expressionName, expression.valueArgumentList, true)
        }
        val arguments = expression.valueArgumentList ?: return null
        return getCallExpression(parent, expression, name, arguments, expressionName, false, isLiteral)
      }
      is KtDotQualifiedExpression -> return getDotQualifiedExpression(parent, psiElement, name, expression)
      is KtParenthesizedExpression -> return createExpressionElement(parent, psiElement, name, expression.expression ?: expression)
      else -> return getExpressionElement(parent, psiElement, name, expression)
    }
  }

  private fun getExpressionElement(parentElement : GradleDslElement,
                                   psiElement : PsiElement,
                                   propertyName: GradleNameElement,
                                   propertyExpression : KtElement) : GradleDslExpression {
    fun unknownElement() : GradleDslExpression {
      parentElement.notification(INCOMPLETE_PARSING).addUnknownElement(propertyExpression)
      return GradleDslUnknownElement(parentElement, propertyExpression, propertyName)
    }

    return when (propertyExpression) {
      // Ex: versionName = 1.0. isQualified = false.
      is KtStringTemplateExpression, is KtConstantExpression -> GradleDslLiteral(
        parentElement, psiElement, propertyName, propertyExpression, false)
      // Ex: compileSdkVersion(SDK_VERSION).
      is KtNameReferenceExpression -> GradleDslLiteral(parentElement, psiElement, propertyName, propertyExpression, true)
      // Ex: KotlinCompilerVersion.VERSION.
      is KtDotQualifiedExpression -> GradleDslLiteral(parentElement, psiElement, propertyName, propertyExpression, true)
      // Ex: Delete::class.
      is KtClassLiteralExpression -> when (val receiverExpression = propertyExpression.receiverExpression) {
        null -> unknownElement()
        else -> GradleDslLiteral(parentElement, psiElement, propertyName, receiverExpression, true)
      }
      // Ex: extra["COMPILE_SDK_VERSION"]
      is KtArrayAccessExpression -> GradleDslLiteral(parentElement, psiElement, propertyName, propertyExpression, true)
      // Ex: extra["COMPILE_SDK_VERSION"]!!, false!!
      is KtPostfixExpression -> when (val baseExpression = propertyExpression.baseExpression) {
        null -> unknownElement()
        else -> getExpressionElement(parentElement, psiElement, propertyName, baseExpression)
      }
      // Ex: extra["foo"] as Boolean, false as Boolean
      is KtBinaryExpressionWithTypeRHS -> getExpressionElement(parentElement, psiElement, propertyName, propertyExpression.left)
      else -> unknownElement()
    }
  }

  private fun getClosureBlock(
    parentElement: GradleDslElement, closableBlock : KtBlockExpression, propertyName: GradleNameElement) : GradleDslClosure {
    val closureElement = GradleDslClosure(parentElement, closableBlock, propertyName)
    closableBlock.statements?.requireNoNulls()?.forEach {
      it.accept(this, closureElement)
    }
    return closureElement
  }

}

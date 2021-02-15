/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.ext.PropertyType
import com.android.tools.idea.gradle.dsl.parser.GradleDslWriter
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement.KTS_KNOWN_CONFIGURATIONS
import com.android.tools.idea.gradle.dsl.parser.elements.FakeElement
import com.android.tools.idea.gradle.dsl.parser.elements.FakeMethodElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainContainer
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement
import com.android.tools.idea.gradle.dsl.parser.findLastPsiElementIn
import com.android.tools.idea.gradle.dsl.parser.maybeTrimForParent
import com.android.tools.idea.gradle.dsl.parser.repositories.MavenRepositoryDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_LIST
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_SET
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.isWhiteSpaceOrNls
import java.lang.UnsupportedOperationException

class KotlinDslWriter : KotlinDslNameConverter, GradleDslWriter {
  override fun moveDslElement(element: GradleDslElement): PsiElement? {
    val anchorAfter = element.anchor ?: return null
    val parentPsiElement = when (element.parent) {
      is ExtDslElement -> findLastPsiElementIn(anchorAfter)?.parent ?: return null
      else -> getParentPsi(element) ?: return null
    }

    val anchor = when (element.parent) {
      is ExtDslElement -> findLastPsiElementIn(anchorAfter)
      else -> getPsiElementForAnchor(parentPsiElement, anchorAfter)
    }

    // Create a dummy element to move the element to.
    val psiFactory = KtPsiFactory(parentPsiElement.project)
    val dummyString = psiFactory.createStringTemplate("toReplace")
    // If the element has no anchor, add it to the beginning of the block.
    val toReplace = if (parentPsiElement is KtBlockExpression && anchorAfter == null) {
      parentPsiElement.addBefore(dummyString, anchor)
    }
    else {
      parentPsiElement.addAfter(dummyString, anchor)
    }

    // Find the element we need to replace.
    var e = element.psiElement ?: return null
    val dslParent = element.parent as? GradlePropertiesDslElement ?: return null

    while (!(e.parent is KtFile ||
             (e.parent is KtCallExpression && (e.parent as KtCallExpression).isBlockElement(dslParent)) ||
             (e.parent is KtBlockExpression && dslParent is ExtDslElement))) {
      if (e.parent == null) {
        e = element.psiElement as PsiElement
        break
      }
      e = e.parent
    }

    // Copy the old PsiElement tree.
    val treeCopy = e.copy()

    // Replace what needs to be replaced.
    val newTree = toReplace.replace(treeCopy)

    // Delete original tree.
    e.delete()

    // Set the new PsiElement.
    element.psiElement = newTree

    return element.psiElement
  }

  internal fun maybeQuoteBits(parts: List<String>) = parts.map { if (it.contains('.')) "`$it`" else it }.joinToString(".")

  override fun createDslElement(element: GradleDslElement): PsiElement? {
    // If we are trying to create an extra block, we should skip this step as we don't use proper blocks for extra properties in KTS.
    if (element is ExtDslElement) return getParentPsi(element)
    val psiElement = element.psiElement
    if (psiElement != null) return psiElement
    var anchorAfter = element.anchor
    var isRealList = false // This is to keep track if we're creating a real list (listOf()).
    var isNamedPropertyMap = false
    var isVarOrProperty = false

    if (element.isNewEmptyBlockElement) return null  // Avoid creation of an empty block.

    if (needToCreateParent(element)) {
      anchorAfter = null
    }
    else if (anchorAfter == null) {
      anchorAfter = (element.parent as? ExtDslElement)?.anchor
    }

    var parentPsiElement = getParentPsi(element) ?: return null

    val project = parentPsiElement.project
    val psiFactory = KtPsiFactory(project)

    val externalNameInfo = maybeTrimForParent(element, this)
    val joinedName = externalNameInfo.externalNameParts.joinToString(".")
    val quotedName = maybeQuoteBits(externalNameInfo.externalNameParts)
    var statementText : String
    val useAssignment = when (val asMethod = externalNameInfo.asMethod) {
      null -> element.shouldUseAssignment()
      else -> !asMethod
    }
    // TODO(xof): this is a bit horrible, and if there are any other examples where we need to adjust the syntax (as opposed to name)
    //  of something depending on its context, try to figure out a useful generalization.
    if (element.parent is DependenciesDslElement && !useAssignment && !KTS_KNOWN_CONFIGURATIONS.contains(joinedName)) {
      statementText = "\"${joinedName}\""
    }
    else if (element is GradleDslNamedDomainElement) {
      val parent = element.parent
      statementText = when {
        // use an existing methodName if we have one
        element.methodName != null -> "${element.methodName}(\"$joinedName\")"
        // use getByName() if the element is implicitly provided, otherwise create()
        parent is GradleDslNamedDomainContainer -> when {
          parent.implicitlyExists(joinedName) -> "getByName(\"$joinedName\")"
          else -> "create(\"$joinedName\")"
        }
        // should never happen (named domain element added to something that isn't a named domain container)
        else -> {
          val log = logger<KotlinDslWriter>()
          log.warn("NamedDomainElement $element added to non-NamedDomainContainer $parent", Throwable())
          "getByName(\"$joinedName\")"
        }
      }
    }
    else {
      statementText = joinedName
    }

    if (element.isBlockElement) {
      if (element is MavenRepositoryDslElement && element.getContainedElements(true).isEmpty()) {
        statementText += "()"
      }
      else {
        statementText += " {\n}"  // Can't create expression with another new line after.
      }
    }
    else if (useAssignment) {
      if (element.elementType == PropertyType.REGULAR) {
        if (element.parent is ExtDslElement) {
          // This is about a regular extra property and should have a dedicated syntax.
          // TODO(b/148769031): For now, we need to be careful about psi to dsl translation in both ways and reflect the dsl logic back to
          //  the psi elements.
          if (element.fullName.startsWith("ext.")) statementText = "extra[\"${joinedName}\"] = \"abc\""
          else {
            statementText = "val $quotedName by extra(\"abc\")"
            isVarOrProperty = true
          }
        }
        else {
          statementText += " = \"abc\""
        }
      }
      else if (element.elementType == PropertyType.VARIABLE) {
        statementText = "val ${quotedName} = \"abc\""
        isVarOrProperty = true
      }
    }
    else if (element is GradleDslExpressionList) {
      val parentDsl = element.parent
      if (parentDsl is GradleDslMethodCall && element.elementType == PropertyType.DERIVED) {
        // This is when we have not a proper list element (listOf()) but rather a methodCall arguments. In such case we need to skip
        // creating the list and use the KtValueArgumentList of the parent.
        return (parentDsl.psiElement as? KtCallExpression)?.valueArgumentList  // TODO add more tests to verify the code consistency.
      }
      else if (element.name.isEmpty()){
        // This is the case where we are handling a list element
        statementText += "listOf()"
        isRealList = true
      }
      else {
        statementText += "()"
      }
    }
    else if (element is GradleDslExpressionMap) {
      if (element.asNamedArgs) {
        statementText += "()"
      }
      else if (element.name.isEmpty()) {
        statementText += "mapOf()"
      }
      else if (element.elementType == PropertyType.DERIVED && element.isLiteralMap) {
        // This is the case of maps within other maps
        statementText = "\"${StringUtil.unquoteString(element.name)}\" to \"abc\""
      }
      else {
        statementText += "(mapOf())"
        isNamedPropertyMap = true
      }
    }
    else {
      statementText += "()"
    }

    val statement = if (isVarOrProperty) psiFactory.createProperty(statementText) else psiFactory.createExpression(statementText)
    when (statement) {
      is KtBinaryExpression -> {
        statement.right?.delete()
      }
      is KtCallExpression -> {
        if (element.isBlockElement) {
          // Add new line to separate blocks statements.
          statement.addAfter(psiFactory.createNewLine(), statement.lastChild)
        }
      }
      is KtProperty -> {
        // If we created a local variable, we need to delete the right value to allow adding the right one.
        if (element.elementType == PropertyType.VARIABLE) {
          statement.initializer?.delete()
        }
        else {
          // This is the case os an extra property, and we will need to delete the value from the extra() callExpression.
          val delegateExpression = statement.delegateExpression as? KtCallExpression ?: return null
          delegateExpression.valueArgumentList?.removeArgument(0)
        }
      }
    }

    val lineTerminator = psiFactory.createNewLine()
    val addedElement : PsiElement
    var anchor = getPsiElementForAnchor(parentPsiElement, anchorAfter)

    when (parentPsiElement) {
      is KtFile -> {
        // If the anchor is null, we would add the new element to the beginning of the file which is correct, unless the file starts
        // with a comment : in such case we need to add the element right after the comment and not before.
        val fileBlock = parentPsiElement.script?.blockExpression
        if (fileBlock != null) {
          parentPsiElement = fileBlock
          anchor = getPsiElementForAnchor(parentPsiElement, anchorAfter)
        }

        val firstRealChild = fileBlock?.firstChild
        if (fileBlock != null && anchor == null && firstRealChild?.node?.elementType == BLOCK_COMMENT) {
          addedElement = fileBlock.addAfter(statement, firstRealChild)
        }
        else {
          addedElement = parentPsiElement.addAfter(statement, anchor)
          if (!isWhiteSpaceOrNls(addedElement.nextSibling)) {
            parentPsiElement.addAfter(lineTerminator, addedElement)
          }
        }

        if (element.isBlockElement && !isWhiteSpaceOrNls(addedElement.prevSibling)) {
          parentPsiElement.addBefore(lineTerminator, addedElement)
        }
        parentPsiElement.addBefore(lineTerminator, addedElement)
      }
      is KtBlockExpression -> {
        addedElement = parentPsiElement.addAfter(statement, anchor)
        if (anchorAfter != null) {
          parentPsiElement.addBefore(lineTerminator, addedElement)
        }
        else {
          parentPsiElement.addAfter(lineTerminator, addedElement)
        }
      }
      is KtValueArgumentList -> {
        val argumentValue = psiFactory.createArgument(statement)
        addedElement = parentPsiElement.addArgumentAfter(argumentValue, anchor as? KtValueArgument)
      }
      is KtCallExpression -> {
        val argumentList = parentPsiElement.valueArgumentList ?: return null
        val argumentValue = psiFactory.createArgument(statement)
        addedElement = argumentList.addArgumentAfter(argumentValue, anchor as? KtValueArgument)?.getArgumentExpression() ?: return null
      }
      else -> {
        addedElement = parentPsiElement.addAfter(statement, anchor)
        parentPsiElement.addBefore(lineTerminator, addedElement)
      }
    }

    if (element.isBlockElement) {
      val blockExpression = getKtBlockExpression(addedElement)
      if (blockExpression != null) {
        element.psiElement = blockExpression
      }
    }
    else if (addedElement is KtBinaryExpression) {
      addedElement.addAfter(psiFactory.createWhiteSpace(), addedElement.lastChild)
      element.psiElement = addedElement
    }
    else if (addedElement is KtCallExpression) {
      if (element is GradleDslExpressionList && !isRealList) {
        element.psiElement = addedElement.valueArgumentList
      }
      else if (element is GradleDslExpressionMap && isNamedPropertyMap) {
        element.psiElement = addedElement.valueArguments[0].getArgumentExpression()
      }
      else {
        element.psiElement = addedElement
      }
    }
    else if (addedElement is KtValueArgument) {
      element.psiElement = addedElement.getArgumentExpression()
    }
    else if (addedElement is KtProperty) {
      element.psiElement = addedElement
    }

    return element.psiElement
  }

  override fun deleteDslElement(element: GradleDslElement) {
    deletePsiElement(element, element.psiElement)
  }

  override fun createDslLiteral(literal: GradleDslLiteral): PsiElement? {
    return when (literal.parent) {
      is GradleDslExpressionList -> createListElement(literal)
      is GradleDslExpressionMap -> createMapElement(literal)
      else -> createDslElement(literal)
    }
  }

  override fun applyDslLiteral(literal: GradleDslLiteral) {
    val psiElement = literal.psiElement ?: return
    maybeUpdateName(literal, this)

    val newLiteral = literal.unsavedValue ?: return
    var psiExpression = literal.expression

    // Earlier, at the parser stage, if the literal value is typed (ex: val as Type), we trim the type psiElement from the expression. Make
    // sure to include it in the psiExpression in case of update.
    if (psiExpression?.parent is KtBinaryExpressionWithTypeRHS) {
      psiExpression = psiExpression.parent
    }

    if (psiExpression != null) {
      val replace = psiExpression.replace(newLiteral)
      // Make sure we replaced with the right psi element for the GradleDslLiteral.
      when (replace) {
        is KtStringTemplateExpression, is KtConstantExpression, is KtNameReferenceExpression, is KtDotQualifiedExpression,
        is KtArrayAccessExpression, is KtBinaryExpressionWithTypeRHS -> literal.setExpression(replace)
        else -> Unit
      }
    }
    else if (psiElement is KtCallExpression) {
      // This element has just been created and will be "propertyName()".
      val valueArgument = KtPsiFactory(newLiteral.project).createArgument(newLiteral as? KtExpression)
      val valueArgumentList = psiElement.valueArgumentList
      val added =
        valueArgumentList?.addArgumentAfter(valueArgument, valueArgumentList?.arguments?.lastOrNull())?.getArgumentExpression() ?: return
      literal.setExpression(added)
    }
    else if (psiElement is KtProperty && psiElement.hasDelegate()) {
      // This is an extra property that has just been created.
      val delegateExpression = requireNotNull(psiElement.delegateExpression as KtCallExpression)
      val valueArgument = KtPsiFactory(newLiteral.project).createArgument(newLiteral as? KtExpression)
      val added = delegateExpression.valueArgumentList?.addArgument(valueArgument)?.getArgumentExpression() ?: return
      literal.setExpression(added)
    }
    else {
      // This element has just been created and will be like "propertyName = " or "val propertyName = ".
      val added = psiElement.addAfter(newLiteral, psiElement.lastChild)
      literal.setExpression(added)
    }

    if (literal.unsavedConfigBlock != null) {
      addConfigBlock(literal)
    }

    literal.reset()
    literal.commit()
  }

  override fun deleteDslLiteral(literal: GradleDslLiteral) {
    deletePsiElement(literal, literal.expression)
  }

  override fun createDslMethodCall(methodCall: GradleDslMethodCall): PsiElement? {
    val psiElement = methodCall.psiElement
    if (psiElement != null && psiElement.isValid) {
      return psiElement
    }

    val methodParent = methodCall.parent ?: return null

    var anchorAfter = methodCall.anchor

    //If the parent doesn't have a psiElement, the anchor will be used to create it. In such case, we need to empty the anchor.
    if (needToCreateParent(methodCall)) {
      anchorAfter = null
    }

    var parentPsiElement = methodParent.create() ?: return null
    val anchor = getPsiElementForAnchor(parentPsiElement, anchorAfter)
    val psiFactory = KtPsiFactory(parentPsiElement.project)

    val fakeElement = FakeMethodElement(methodCall)

    val statementText =
      if (methodCall.fullName.isNotEmpty() && methodCall.fullName != methodCall.methodName) {
        val externalNameInfo = maybeTrimForParent(methodCall, this)
        var propertyName = externalNameInfo.externalNameParts.joinToString(".")
        // If we are writing a project property, we should be make sure to use double quotes instead of single quotes
        // since we use single quotes for ProjectPropertiesDslElement.
        if (propertyName.startsWith("project(':")) {
          propertyName = propertyName.replace("\\s".toRegex(), "").replace("'", "\"")
        }
        val useAssignment = when (val asMethod = externalNameInfo.asMethod) {
          null -> methodCall.shouldUseAssignment()
          else -> !asMethod
        }
        var methodName = maybeTrimForParent(fakeElement, this).externalNameParts.joinToString(".")
        if (useAssignment) {
          // Ex: a = b().
          "$propertyName = $methodName()"
        }
        else {
          // Ex: implementation(fileTree()), "feature"(fileTree())
          if (methodCall.parent is DependenciesDslElement && !useAssignment && !KTS_KNOWN_CONFIGURATIONS.contains(propertyName)) {
            propertyName = "\"$propertyName\""
          }
          "$propertyName($methodName())"
        }
      }
    else {
        // Ex : proguardFile() where the name is the same as the methodName, so we need to make sure we create one method only.
        maybeTrimForParent(fakeElement, this).externalNameParts.joinToString(".") + "()"
      }
    val expression = psiFactory.createExpression(statementText)

    val addedElement :PsiElement
    if (parentPsiElement is KtValueArgumentList) {
      val valueArgument = psiFactory.createArgument(expression)
      val addedArgument = parentPsiElement.addArgumentAfter(valueArgument, anchor as? KtValueArgument)
      addedElement = requireNotNull(addedArgument.getArgumentExpression())
    }
    else {
      // If the parent is a KtFile, we should be careful adding the methodCall to it's main block.
      if (parentPsiElement is KtFile) {
        parentPsiElement = parentPsiElement.script?.blockExpression ?: parentPsiElement
        addedElement = parentPsiElement.addAfter(expression, anchor)
      }
      else {
        addedElement = parentPsiElement.addAfter(expression, anchor)
      }
      // We need to add empty lines if we're adding expressions to a file because IDEA doesn't handle formatting
      // in kotlin the same way as GROOVY.
      val lineTerminator = psiFactory.createNewLine()
      if (addedElement.nextSibling != null && !isWhiteSpaceOrNls(addedElement.nextSibling)) {
        parentPsiElement.addAfter(lineTerminator, addedElement)
      }
      if (anchor != null && !hasNewLineBetween(anchor, addedElement)) {
        parentPsiElement.addBefore(lineTerminator, addedElement)
      }
    }

    if (addedElement is KtBinaryExpression) {
      val addedMethodExpression = addedElement.right ?: return null
      when (addedMethodExpression) {
        is KtDotQualifiedExpression -> {
          val callExpression = addedMethodExpression.selectorExpression as? KtCallExpression ?: return null
          methodCall.psiElement = callExpression
          methodCall.argumentsElement.psiElement = callExpression.valueArgumentList
          return methodCall.psiElement
        }
        is KtCallExpression -> {
          methodCall.psiElement = addedMethodExpression
          methodCall.argumentsElement.psiElement = addedMethodExpression.valueArgumentList
          return methodCall.psiElement
        }
        else -> return null
      }
    }

    val argumentList = (addedElement as? KtCallExpression)?.valueArgumentList?.arguments ?: return null
    // TODO(xof): I think this is meant to handle the case where we have a non-assignment method call with name distinct from methodName,
    //  such as setRoot(file("...")) such as we might get through transforms.  It looks like it might work for simple cases, but e.g.
    //  setStorePassword(System.getenv("FOO")) fails this test because argumentList[0].getArgumentExpression() is a
    //  KtDotQualifiedExpression, not a KtCallExpression.
    if (argumentList.size == 1 && argumentList[0].getArgumentExpression() is KtCallExpression) {
      methodCall.psiElement = argumentList[0].getArgumentExpression()
      methodCall.argumentsElement.psiElement = (argumentList[0].getArgumentExpression() as KtCallExpression).valueArgumentList
      return methodCall.psiElement
    }
    else if (argumentList.isEmpty()) {
      methodCall.psiElement = addedElement
      methodCall.argumentsElement.psiElement = addedElement.valueArgumentList

      val unsavedClosure = methodCall.unsavedClosure
      if (unsavedClosure != null) {
        createAndAddClosure(unsavedClosure, methodCall)
      }
      return methodCall.psiElement
    }

    return null
  }

  override fun applyDslMethodCall(methodCall: GradleDslMethodCall) {
    maybeUpdateName(methodCall, this)
    methodCall.argumentsElement.applyChanges()
    val unsavedClosure = methodCall.unsavedClosure
    if (unsavedClosure != null) {
      createAndAddClosure(unsavedClosure, methodCall)
    }
  }

  override fun createDslExpressionList(expressionList: GradleDslExpressionList): PsiElement? {
    // GradleDslExpressionList represents list objects as well as method arguments.
    var psiElement = expressionList.psiElement
    if (psiElement != null) {
      return psiElement
    }
    else {
      if (expressionList.parent is GradleDslExpressionMap) {
        // The list is an entry in a map, and we need to create a binaryExpression for it.
        return createBinaryExpression(expressionList)
      }
      psiElement = createDslElement(expressionList) ?: return null
    }

    if (psiElement is KtCallExpression) return psiElement

    val emptyListText = when (expressionList.modelProperty?.type) {
      MUTABLE_LIST -> "mutableListOf()"
      MUTABLE_SET -> "mutableSetOf()"
      else -> "listOf()"
    }

    if (psiElement is KtBinaryExpression) {
      val emptyList = KtPsiFactory(psiElement.project).createExpression(emptyListText)
      val added = psiElement.addAfter(emptyList, psiElement.lastChild)
      expressionList.psiElement = added
      return expressionList.psiElement
    }
    else if (psiElement is KtValueArgumentList) { // When the dsl list resolves to a callExpression arguments.
      if (expressionList.expressions.size == 1 && psiElement.arguments.size == 1 && !expressionList.isAppendToArgumentListWithOneElement) {
        // Sometimes we don't want to allow adding to a list that has one argument (ex : proguardFile("xyz")).
        expressionList.psiElement = null
        psiElement = createDslElement(expressionList)
      }
      return psiElement
    }
    else if (psiElement is KtProperty) {
      if (psiElement.hasDelegate()) {
        // This is the case of a property with a delegate (ex: extra property).
        val delegateExpressionArgs = (psiElement.delegateExpression as? KtCallExpression)?.valueArgumentList ?: return null
        val valueArgument = KtPsiFactory(psiElement.project).createArgument(emptyListText)
        val listElement = delegateExpressionArgs.addArgument(valueArgument).getArgumentExpression() ?: return null
        expressionList.psiElement = listElement
        return expressionList.psiElement
      }
      else {
        // This should be the case of a property with an initializer (ex: val prop = listOf()).
        val emptyList = KtPsiFactory(psiElement.project).createExpression(emptyListText)
        val added = psiElement.addAfter(emptyList, psiElement.lastChild)
        expressionList.psiElement = added
        return expressionList.psiElement
      }

    }

    return null
  }

  override fun applyDslExpressionList(expressionList: GradleDslExpressionList) {
    maybeUpdateName(expressionList, this)
  }

  override fun createDslExpressionMap(expressionMap: GradleDslExpressionMap): PsiElement? {
    if (expressionMap.psiElement != null) return expressionMap.psiElement

    val psiElement = createDslElement(expressionMap) ?: return null
    val psiFactory = KtPsiFactory(psiElement.project)
    if (psiElement is KtBinaryExpression || (psiElement is KtProperty && !psiElement.hasDelegate())) {
      val emptyMapExpression = psiFactory.createExpression("mapOf()")
      val mapElement = psiElement.addAfter(emptyMapExpression, psiElement.lastChild)
      expressionMap.psiElement = mapElement
    }
    else if (psiElement is KtProperty && psiElement.hasDelegate()) {
      // This is the case of an extra property, and the map is used as the property delegate expression.
      val delegateExpressionArgs = (psiElement.delegateExpression as? KtCallExpression)?.valueArgumentList ?: return null
      val valueArgument = psiFactory.createArgument("mapOf()")
      val mapElement = delegateExpressionArgs.addArgument(valueArgument).getArgumentExpression() ?: return null
      expressionMap.psiElement = mapElement
    }
    return expressionMap.psiElement
  }

  override fun applyDslExpressionMap(expressionMap: GradleDslExpressionMap) {
    maybeUpdateName(expressionMap, this)
  }

  override fun applyDslPropertiesElement(element: GradlePropertiesDslElement) {
    maybeUpdateName(element, this)
  }
}

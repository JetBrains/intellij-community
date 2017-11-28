/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.debugger

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.util.SmartList
import com.intellij.util.ThreeState
import com.intellij.xdebugger.XSourcePositionWrapper
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.presentation.XKeywordValuePresentation
import com.intellij.xdebugger.frame.presentation.XNumericValuePresentation
import com.intellij.xdebugger.frame.presentation.XStringValuePresentation
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import org.jetbrains.concurrency.*
import org.jetbrains.debugger.values.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import javax.swing.Icon

fun VariableView(variable: Variable, context: VariableContext) = VariableView(variable.name, variable, context)

class VariableView(override val variableName: String, private val variable: Variable, private val context: VariableContext) : XNamedValue(variableName), VariableContext {
  @Volatile private var value: Value? = null
  // lazy computed
  private var _memberFilter: MemberFilter? = null

  @Volatile private var remainingChildren: List<Variable>? = null
  @Volatile private var remainingChildrenOffset: Int = 0

  override fun watchableAsEvaluationExpression() = context.watchableAsEvaluationExpression()

  override val viewSupport: DebuggerViewSupport
    get() = context.viewSupport

  override val parent = context

  override val memberFilter: Promise<MemberFilter>
    get() = context.viewSupport.getMemberFilter(this)

  override val evaluateContext: EvaluateContext
    get() = context.evaluateContext

  override val scope: Scope?
    get() = context.scope

  override val vm: Vm?
    get() = context.vm

  override fun computePresentation(node: XValueNode, place: XValuePlace) {
    value = variable.value
    if (value != null) {
      computePresentation(value!!, node)
      return
    }

    if (variable !is ObjectProperty || variable.getter == null) {
      // it is "used" expression (WEB-6779 Debugger/Variables: Automatically show used variables)
      evaluateContext.evaluate(variable.name)
        .done(node) {
          if (it.wasThrown) {
            setEvaluatedValue(viewSupport.transformErrorOnGetUsedReferenceValue(it.value, null), null, node)
          }
          else {
            value = it.value
            computePresentation(it.value, node)
          }
        }
        .rejected(node) { setEvaluatedValue(viewSupport.transformErrorOnGetUsedReferenceValue(null, it.message), it.message, node) }
      return
    }

    node.setPresentation(null, object : XValuePresentation() {
      override fun renderValue(renderer: XValuePresentation.XValueTextRenderer) {
        renderer.renderValue("\u2026")
      }
    }, false)
    node.setFullValueEvaluator(object : XFullValueEvaluator(" (invoke getter)") {
      override fun startEvaluation(callback: XFullValueEvaluator.XFullValueEvaluationCallback) {
        var valueModifier = variable.valueModifier
        var nonProtoContext = context
        while (nonProtoContext is VariableView && nonProtoContext.variableName == PROTOTYPE_PROP) {
          valueModifier = nonProtoContext.variable.valueModifier
          nonProtoContext = nonProtoContext.parent
        }
        valueModifier!!.evaluateGet(variable, evaluateContext)
          .done(node) {
            callback.evaluated("")
            setEvaluatedValue(it, null, node)
          }
      }
    }.setShowValuePopup(false))
  }

  private fun setEvaluatedValue(value: Value?, error: String?, node: XValueNode) {
    if (value == null) {
      node.setPresentation(AllIcons.Debugger.Db_primitive, null, error ?: "Internal Error", false)
    }
    else {
      this.value = value
      computePresentation(value, node)
    }
  }

  private fun computePresentation(value: Value, node: XValueNode) {
    when (value.type) {
      ValueType.OBJECT, ValueType.NODE -> context.viewSupport.computeObjectPresentation((value as ObjectValue), variable, context, node, icon)

      ValueType.FUNCTION -> node.setPresentation(icon, ObjectValuePresentation(trimFunctionDescription(value)), true)

      ValueType.ARRAY -> context.viewSupport.computeArrayPresentation(value, variable, context, node, icon)

      ValueType.BOOLEAN, ValueType.NULL, ValueType.UNDEFINED -> node.setPresentation(icon, XKeywordValuePresentation(value.valueString!!), false)

      ValueType.NUMBER -> node.setPresentation(icon, createNumberPresentation(value.valueString!!), false)

      ValueType.STRING -> {
        node.setPresentation(icon, XStringValuePresentation(value.valueString!!), false)
        // isTruncated in terms of debugger backend, not in our terms (i.e. sometimes we cannot control truncation),
        // so, even in case of StringValue, we check value string length
        if ((value is StringValue && value.isTruncated) || value.valueString!!.length > XValueNode.MAX_VALUE_LENGTH) {
          node.setFullValueEvaluator(MyFullValueEvaluator(value))
        }
      }

      else -> node.setPresentation(icon, null, value.valueString!!, true)
    }
  }

  override fun computeChildren(node: XCompositeNode) {
    node.setAlreadySorted(true)

    if (value !is ObjectValue) {
      node.addChildren(XValueChildrenList.EMPTY, true)
      return
    }

    val list = remainingChildren
    if (list != null) {
      val to = Math.min(remainingChildrenOffset + XCompositeNode.MAX_CHILDREN_TO_SHOW, list.size)
      val isLast = to == list.size
      node.addChildren(createVariablesList(list, remainingChildrenOffset, to, this, _memberFilter), isLast)
      if (!isLast) {
        node.tooManyChildren(list.size - to)
        remainingChildrenOffset += XCompositeNode.MAX_CHILDREN_TO_SHOW
      }
      return
    }

    val objectValue = value as ObjectValue
    val hasNamedProperties = objectValue.hasProperties() != ThreeState.NO
    val hasIndexedProperties = objectValue.hasIndexedProperties() != ThreeState.NO
    val promises = SmartList<Promise<*>>()
    val additionalProperties = viewSupport.computeAdditionalObjectProperties(objectValue, variable, this, node)
    if (additionalProperties != null) {
      promises.add(additionalProperties)
    }

    // we don't support indexed properties if additional properties added - behavior is undefined if object has indexed properties and additional properties also specified
    if (hasIndexedProperties) {
      promises.add(computeIndexedProperties(objectValue as ArrayValue, node, !hasNamedProperties && additionalProperties == null))
    }

    if (hasNamedProperties) {
      // named properties should be added after additional properties
      if (additionalProperties == null || additionalProperties.state != Promise.State.PENDING) {
        promises.add(computeNamedProperties(objectValue, node, !hasIndexedProperties && additionalProperties == null))
      }
      else {
        promises.add(additionalProperties.thenAsync(node) { computeNamedProperties(objectValue, node, true) })
      }
    }

    if (hasIndexedProperties == hasNamedProperties || additionalProperties != null) {
      all(promises).processed(node) { node.addChildren(XValueChildrenList.EMPTY, true) }
    }
  }

  abstract class ObsolescentIndexedVariablesConsumer(protected val node: XCompositeNode) : IndexedVariablesConsumer() {
    override val isObsolete: Boolean
      get() = node.isObsolete
  }

  private fun computeIndexedProperties(value: ArrayValue, node: XCompositeNode, isLastChildren: Boolean): Promise<*> {
    return value.getIndexedProperties(0, value.length, XCompositeNode.MAX_CHILDREN_TO_SHOW, object : ObsolescentIndexedVariablesConsumer(node) {
      override fun consumeRanges(ranges: IntArray?) {
        if (ranges == null) {
          val groupList = XValueChildrenList()
          addGroups(value, ::lazyVariablesGroup, groupList, 0, value.length, XCompositeNode.MAX_CHILDREN_TO_SHOW, this@VariableView)
          node.addChildren(groupList, isLastChildren)
        }
        else {
          addRanges(value, ranges, node, this@VariableView, isLastChildren)
        }
      }

      override fun consumeVariables(variables: List<Variable>) {
        node.addChildren(createVariablesList(variables, this@VariableView, null), isLastChildren)
      }
    })
  }

  private fun computeNamedProperties(value: ObjectValue, node: XCompositeNode, isLastChildren: Boolean) = processVariables(this, value.properties, node) { memberFilter, variables ->
    _memberFilter = memberFilter

    if (value.type == ValueType.ARRAY && value !is ArrayValue) {
      computeArrayRanges(variables, node)
      return@processVariables
    }

    var functionValue = value as? FunctionValue
    if (functionValue != null && functionValue.hasScopes() == ThreeState.NO) {
      functionValue = null
    }

    remainingChildren = processNamedObjectProperties(variables, node, this@VariableView, memberFilter, XCompositeNode.MAX_CHILDREN_TO_SHOW, isLastChildren && functionValue == null)
    if (remainingChildren != null) {
      remainingChildrenOffset = XCompositeNode.MAX_CHILDREN_TO_SHOW
    }

    if (functionValue != null) {
      // we pass context as variable context instead of this variable value - we cannot watch function scopes variables, so, this variable name doesn't matter
      node.addChildren(XValueChildrenList.bottomGroup(FunctionScopesValueGroup(functionValue, context)), isLastChildren)
    }
  }

  private fun computeArrayRanges(properties: List<Variable>, node: XCompositeNode) {
    val variables = filterAndSort(properties, _memberFilter!!)
    var count = variables.size
    val bucketSize = XCompositeNode.MAX_CHILDREN_TO_SHOW
    if (count <= bucketSize) {
      node.addChildren(createVariablesList(variables, this, null), true)
      return
    }

    while (count > 0) {
      if (Character.isDigit(variables.get(count - 1).name[0])) {
        break
      }
      count--
    }

    val groupList = XValueChildrenList()
    if (count > 0) {
      addGroups(variables, ::createArrayRangeGroup, groupList, 0, count, bucketSize, this)
    }

    var notGroupedVariablesOffset: Int
    if ((variables.size - count) > bucketSize) {
      notGroupedVariablesOffset = variables.size
      while (notGroupedVariablesOffset > 0) {
        if (!variables.get(notGroupedVariablesOffset - 1).name.startsWith("__")) {
          break
        }
        notGroupedVariablesOffset--
      }

      if (notGroupedVariablesOffset > 0) {
        addGroups(variables, ::createArrayRangeGroup, groupList, count, notGroupedVariablesOffset, bucketSize, this)
      }
    }
    else {
      notGroupedVariablesOffset = count
    }

    for (i in notGroupedVariablesOffset..variables.size - 1) {
      val variable = variables.get(i)
      groupList.add(VariableView(_memberFilter!!.rawNameToSource(variable), variable, this))
    }

    node.addChildren(groupList, true)
  }

  private val icon: Icon
    get() = getIcon(value!!)

  override fun getModifier(): XValueModifier? {
    if (!variable.isMutable) {
      return null
    }

    return object : XValueModifier() {
      override fun getInitialValueEditorText(): String? {
        if (value!!.type == ValueType.STRING) {
          val string = value!!.valueString!!
          val builder = StringBuilder(string.length)
          builder.append('"')
          StringUtil.escapeStringCharacters(string.length, string, builder)
          builder.append('"')
          return builder.toString()
        }
        else {
          return if (value!!.type.isObjectType) null else value!!.valueString
        }
      }

      override fun setValue(expression: String, callback: XValueModifier.XModificationCallback) {
        variable.valueModifier!!.setValue(variable, expression, evaluateContext)
          .doneRun {
            value = null
            callback.valueModified()
          }
          .rejected { callback.errorOccurred(it.message!!) }
      }
    }
  }

  fun getValue() = variable.value

  override fun canNavigateToSource() = value is FunctionValue || viewSupport.canNavigateToSource(variable, context)

  override fun computeSourcePosition(navigatable: XNavigatable) {
    if (value is FunctionValue) {
      (value as FunctionValue).resolve()
        .done { function ->
          vm!!.scriptManager.getScript(function)
            .done {
              navigatable.setSourcePosition(it?.let { viewSupport.getSourceInfo(null, it, function.openParenLine, function.openParenColumn) }?.let {
                object : XSourcePositionWrapper(it) {
                  override fun createNavigatable(project: Project): Navigatable {
                    return PsiVisitors.visit(myPosition, project) { position, element, positionOffset, document ->
                      // element will be "open paren", but we should navigate to function name,
                      // we cannot use specific PSI type here (like JSFunction), so, we try to find reference expression (i.e. name expression)
                      var referenceCandidate: PsiElement? = element
                      var psiReference: PsiElement? = null
                      while (true) {
                        referenceCandidate = referenceCandidate?.prevSibling ?: break
                        if (referenceCandidate is PsiReference) {
                          psiReference = referenceCandidate
                          break
                        }
                      }

                      if (psiReference == null) {
                        referenceCandidate = element.parent
                        while (true) {
                          referenceCandidate = referenceCandidate?.prevSibling ?: break
                          if (referenceCandidate is PsiReference) {
                            psiReference = referenceCandidate
                            break
                          }
                        }
                      }

                      (if (psiReference == null) element.navigationElement else psiReference.navigationElement) as? Navigatable
                    } ?: super.createNavigatable(project)
                  }
                }
              })
            }
        }
    }
    else {
      viewSupport.computeSourcePosition(variableName, value!!, variable, context, navigatable)
    }
  }

  override fun computeInlineDebuggerData(callback: XInlineDebuggerDataCallback) = viewSupport.computeInlineDebuggerData(variableName, variable, context, callback)

  override fun getEvaluationExpression(): String? {
    if (!watchableAsEvaluationExpression()) {
      return null
    }
    if (context.variableName == null) return variable.name // top level watch expression, may be call etc.

    val list = SmartList<String>()
    addVarName(list, parent, variable.name)

    var parent: VariableContext? = context
    while (parent != null && parent.variableName != null) {
      addVarName(list, parent.parent, parent.variableName!!)
      parent = parent.parent
    }
    return context.viewSupport.propertyNamesToString(list, false)
  }

  private fun addVarName(list: SmartList<String>, parent: VariableContext?, name: String) {
    if (parent == null || parent.variableName != null) list.add(name)
    else list.addAll(name.split(".").reversed())
  }

  private class MyFullValueEvaluator(private val value: Value) : XFullValueEvaluator(if (value is StringValue) value.length else value.valueString!!.length) {
    override fun startEvaluation(callback: XFullValueEvaluator.XFullValueEvaluationCallback) {
      if (value !is StringValue || !value.isTruncated) {
        callback.evaluated(value.valueString!!)
        return
      }

      val evaluated = AtomicBoolean()
      value.fullString
        .done {
          if (!callback.isObsolete && evaluated.compareAndSet(false, true)) {
            callback.evaluated(value.valueString!!)
          }
        }
        .rejected { callback.errorOccurred(it.message!!) }
    }
  }

  companion object {
    fun setObjectPresentation(value: ObjectValue, icon: Icon, node: XValueNode) {
      node.setPresentation(icon, ObjectValuePresentation(getObjectValueDescription(value)), value.hasProperties() != ThreeState.NO)
    }

    fun setArrayPresentation(value: Value, context: VariableContext, icon: Icon, node: XValueNode) {
      assert(value.type == ValueType.ARRAY)

      if (value is ArrayValue) {
        val length = value.length
        node.setPresentation(icon, ArrayPresentation(length, value.className), length > 0)
        return
      }

      val valueString = value.valueString
      // only WIP reports normal description
      if (valueString != null && (valueString.endsWith(")") || valueString.endsWith(']')) &&
          ARRAY_DESCRIPTION_PATTERN.matcher(valueString).find()) {
        node.setPresentation(icon, null, valueString, true)
      }
      else {
        context.evaluateContext.evaluate("a.length", Collections.singletonMap<String, Any>("a", value), false)
          .done(node) { node.setPresentation(icon, null, "Array[${it.value.valueString}]", true) }
          .rejected(node) {
            logger<VariableView>().error("Failed to evaluate array length: $it")
            node.setPresentation(icon, null, valueString ?: "Array", true)
          }
      }
    }

    fun getIcon(value: Value): Icon {
      val type = value.type
      return when (type) {
        ValueType.FUNCTION -> AllIcons.Nodes.Function
        ValueType.ARRAY -> AllIcons.Debugger.Db_array
        else -> if (type.isObjectType) AllIcons.Debugger.Value else AllIcons.Debugger.Db_primitive
      }
    }
  }
}

fun getClassName(value: ObjectValue): String {
  val className = value.className
  return if (className.isNullOrEmpty()) "Object" else className!!
}

fun getObjectValueDescription(value: ObjectValue): String {
  val description = value.valueString
  return if (description.isNullOrEmpty()) getClassName(value) else description!!
}

internal fun trimFunctionDescription(value: Value): String {
  return trimFunctionDescription(value.valueString ?: return "")
}

fun trimFunctionDescription(value: String): String {
  var endIndex = 0
  while (endIndex < value.length && !StringUtil.isLineBreak(value[endIndex])) {
    endIndex++
  }
  while (endIndex > 0 && Character.isWhitespace(value[endIndex - 1])) {
    endIndex--
  }
  return value.substring(0, endIndex)
}

private fun createNumberPresentation(value: String): XValuePresentation {
  return if (value == PrimitiveValue.NA_N_VALUE || value == PrimitiveValue.INFINITY_VALUE) XKeywordValuePresentation(value) else XNumericValuePresentation(value)
}

private val ARRAY_DESCRIPTION_PATTERN = Pattern.compile("^[a-zA-Z\\d]+[\\[(]\\d+[\\])]$")

private class ArrayPresentation(length: Int, className: String?) : XValuePresentation() {
  private val length = Integer.toString(length)
  private val className = if (className.isNullOrEmpty()) "Array" else className!!

  override fun renderValue(renderer: XValuePresentation.XValueTextRenderer) {
    renderer.renderSpecialSymbol(className)
    renderer.renderSpecialSymbol("[")
    renderer.renderSpecialSymbol(length)
    renderer.renderSpecialSymbol("]")
  }
}

private val PROTOTYPE_PROP = "__proto__"
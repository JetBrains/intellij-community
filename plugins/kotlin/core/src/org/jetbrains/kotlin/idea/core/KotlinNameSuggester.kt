// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.idea.core.util.KotlinIdeaCoreBundle
import org.jetbrains.kotlin.idea.util.application.executeInBackgroundWithProgress
import org.jetbrains.kotlin.idea.util.application.isDispatchThread
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getOutermostParenthesizerOrThis
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.callUtil.getParentResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.types.ErrorUtils
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.types.typeUtil.builtIns

object KotlinNameSuggester : AbstractNameSuggester() {
    fun suggestNamesByExpressionAndType(
        expression: KtExpression,
        type: KotlinType?,
        bindingContext: BindingContext?,
        validator: (String) -> Boolean,
        defaultName: String?
    ): Collection<String> {
        return executeInBackgroundWithProgress(expression.project) {
            LinkedHashSet<String>().apply {
                addNamesByExpression(expression, bindingContext, validator)

                (type ?: bindingContext?.getType(expression))?.let {
                    addNamesByType(it, validator)
                }

                if (isEmpty()) {
                    addName(defaultName, validator)
                }
            }.toList()
        }
    }

    fun suggestNamesByType(type: KotlinType, validator: (String) -> Boolean, defaultName: String? = null): List<String> =
        executeInBackgroundWithProgress(null) {
            ArrayList<String>().apply {
                addNamesByType(type, validator)
                if (isEmpty()) {
                    ProgressManager.checkCanceled()
                    addName(defaultName, validator)
                }
            }
        }

    private fun executeInBackgroundWithProgress(project: Project?, blockToExecute: () -> List<String>): List<String> =
        if (isDispatchThread() && !ApplicationManager.getApplication().isWriteAccessAllowed) {
            executeInBackgroundWithProgress(
                project,
                KotlinIdeaCoreBundle.message("progress.title.calculating.names")
            ) { runReadAction { blockToExecute() } }
        } else {
            blockToExecute()
        }

    fun suggestNamesByExpressionOnly(
        expression: KtExpression,
        bindingContext: BindingContext?,
        validator: (String) -> Boolean, defaultName: String? = null
    ): List<String> {
        val result = ArrayList<String>()

        result.addNamesByExpression(expression, bindingContext, validator)

        if (result.isEmpty()) {
            result.addName(defaultName, validator)
        }

        return result
    }

    fun suggestIterationVariableNames(
        collection: KtExpression,
        elementType: KotlinType,
        bindingContext: BindingContext?,
        validator: (String) -> Boolean, defaultName: String?
    ): Collection<String> {
        val result = LinkedHashSet<String>()

        suggestNamesByExpressionOnly(collection, bindingContext, { true })
            .mapNotNull { name -> StringUtil.unpluralize(name)}
            .filter { name -> !name.isKeyword() }
            .mapTo(result) { suggestNameByName(it, validator) }

        result.addNamesByType(elementType, validator)

        if (result.isEmpty()) {
            result.addName(defaultName, validator)
        }

        return result
    }

    private fun String?.isKeyword() = this in KtTokens.KEYWORDS.types.map { it.toString() }

    private fun MutableCollection<String>.addNamesByType(type: KotlinType, validator: (String) -> Boolean) {
        val myType = TypeUtils.makeNotNullable(type) // wipe out '?'
        val builtIns = myType.builtIns
        val typeChecker = KotlinTypeChecker.DEFAULT
        if (ErrorUtils.containsErrorType(myType)) return
        val typeDescriptor = myType.constructor.declarationDescriptor
        when {
            typeChecker.equalTypes(builtIns.booleanType, myType) -> addName("b", validator)
            typeChecker.equalTypes(builtIns.intType, myType) -> addName("i", validator)
            typeChecker.equalTypes(builtIns.byteType, myType) -> addName("byte", validator)
            typeChecker.equalTypes(builtIns.longType, myType) -> addName("l", validator)
            typeChecker.equalTypes(builtIns.floatType, myType) -> addName("fl", validator)
            typeChecker.equalTypes(builtIns.doubleType, myType) -> addName("d", validator)
            typeChecker.equalTypes(builtIns.shortType, myType) -> addName("sh", validator)
            typeChecker.equalTypes(builtIns.charType, myType) -> addName("c", validator)
            typeChecker.equalTypes(builtIns.stringType, myType) -> addName("s", validator)
            myType.isFunctionType -> addName("function", validator)
            KotlinBuiltIns.isArray(myType) || KotlinBuiltIns.isPrimitiveArray(myType) -> {
                addNamesForArray(builtIns, myType, validator, typeChecker)
            }
            typeDescriptor != null && DescriptorUtils.isSubtypeOfClass(typeDescriptor.defaultType, builtIns.iterable.original)
                    && type.arguments.isNotEmpty() ->
                addNameForIterableInheritors(type, validator)
            else -> {
                val name = getTypeName(myType)
                if (name != null) {
                    addCamelNames(name, validator)
                }
                addNamesFromGenericParameters(myType, validator)
            }
        }
    }

    private fun MutableCollection<String>.addNamesForArray(
        builtIns: KotlinBuiltIns,
        myType: KotlinType,
        validator: (String) -> Boolean,
        typeChecker: KotlinTypeChecker
    ) {
        val elementType = builtIns.getArrayElementType(myType)
        val className = getTypeName(elementType)
        if (className != null) {
            addCamelNames(StringUtil.pluralize(className), validator)
            if (!typeChecker.equalTypes(builtIns.booleanType, elementType) &&
                !typeChecker.equalTypes(builtIns.intType, elementType) &&
                !typeChecker.equalTypes(builtIns.byteType, elementType) &&
                !typeChecker.equalTypes(builtIns.longType, elementType) &&
                !typeChecker.equalTypes(builtIns.floatType, elementType) &&
                !typeChecker.equalTypes(builtIns.doubleType, elementType) &&
                !typeChecker.equalTypes(builtIns.shortType, elementType) &&
                !typeChecker.equalTypes(builtIns.charType, elementType) &&
                !typeChecker.equalTypes(builtIns.stringType, elementType)
            ) {
                addName("arrayOf" + StringUtil.capitalize(className) + "s", validator)
            }
        }
    }

    private fun MutableCollection<String>.addNameForIterableInheritors(type: KotlinType, validator: (String) -> Boolean) {
        val typeArgument = type.arguments.singleOrNull()?.type ?: return
        val name = getTypeName(typeArgument)
        if (name != null) {
            addCamelNames(StringUtil.pluralize(name), validator)
            val typeName = getTypeName(type)
            if (typeName != null) {
                addCamelNames(name + typeName, validator)
            }
        }
    }

    private fun MutableCollection<String>.addNamesFromGenericParameters(type: KotlinType, validator: (String) -> Boolean) {
        val typeName = getTypeName(type) ?: return
        val arguments = type.arguments
        val builder = StringBuilder()
        if (arguments.isEmpty()) return
        for (argument in arguments) {
            val name = getTypeName(argument.type)
            if (name != null) {
                builder.append(name)
            }
        }
        addCamelNames(builder.append(typeName).toString(), validator)
    }

    private fun getTypeName(type: KotlinType): String? {
        val descriptor = type.constructor.declarationDescriptor
        if (descriptor != null) {
            val className = descriptor.name
            if (!className.isSpecial) {
                return className.asString()
            }
        }
        return null
    }

    private fun MutableCollection<String>.addNamesByExpression(
        expression: KtExpression?,
        bindingContext: BindingContext?,
        validator: (String) -> Boolean
    ) {
        if (expression == null) return

        addNamesByValueArgument(expression, bindingContext, validator)
        addNamesByExpressionPSI(expression, validator)
    }

    private fun MutableCollection<String>.addNamesByValueArgument(
        expression: KtExpression,
        bindingContext: BindingContext?,
        validator: (String) -> Boolean
    ) {
        if (bindingContext == null) return
        val argumentExpression = expression.getOutermostParenthesizerOrThis()
        val valueArgument = argumentExpression.parent as? KtValueArgument ?: return
        val resolvedCall = argumentExpression.getParentResolvedCall(bindingContext) ?: return
        val argumentMatch = resolvedCall.getArgumentMapping(valueArgument) as? ArgumentMatch ?: return
        val parameter = argumentMatch.valueParameter
        if (parameter.containingDeclaration.hasStableParameterNames()) {
            addName(parameter.name.asString(), validator)
        }
    }

}

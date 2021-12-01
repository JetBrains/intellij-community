// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:OptIn(ExperimentalStdlibApi::class)

package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.gradle.api.logging.Logger
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.typeOf

internal inline fun <reified T> returnType(): TypeToken<T> = TypeToken.create()

internal inline fun <reified T : Any> parameter(value: T?) = Parameter(TypeToken.create(), value)

internal fun parameters(vararg parameter: Parameter<*>): ParameterList =
    if (parameter.isEmpty()) ParameterList.empty else ParameterList(parameter.toList())

internal class TypeToken<T> private constructor(val type: KType) {
    override fun toString(): String {
        return type.toString()
    }

    companion object {
        inline fun <reified T> create(): TypeToken<T> {
            return TypeToken(typeOf<T>())
        }
    }
}

internal class Parameter<T : Any>(val typeToken: TypeToken<T>, val value: T?)

internal class ParameterList(private val parameters: List<Parameter<*>>) : List<Parameter<*>> by parameters {
    companion object {
        val empty = ParameterList(emptyList())
    }
}

internal inline fun <reified T> Any.callReflective(
    methodName: String, params: ParameterList, returnTypeProjection: TypeToken<T>, logger: Logger
): T? {
    val reportIssue = { message: String, reason: Throwable? ->
        assert(false) { message }
        logger.error(message, reason)
    }

    val method = try {
        this::class.java.getMethod(methodName, *params.map { it.typeToken.type.jvmErasure.java }.toTypedArray())
    } catch (e: Exception) {
        reportIssue("Failed to invoke $methodName on ${this.javaClass.name}", e)
        return null
    }
    method.trySetAccessible()

    val returnValue = try {
        method.invoke(this, *params.map { it.value }.toTypedArray())
    } catch (t: Throwable) {
        reportIssue("Failed to invoke $methodName on ${this.javaClass.name}", t)
        return null
    }

    if (returnValue == null) {
        if (!returnTypeProjection.type.isMarkedNullable) {
            reportIssue("Method $methodName on ${this.javaClass.name} unexpectedly returned null (expected $returnTypeProjection)", null)
        }
        return null
    }

    if (!returnTypeProjection.type.jvmErasure.isInstance(returnValue)) {
        reportIssue(
            "Method $methodName on ${this.javaClass.name} unexpectedly returned ${returnValue.javaClass.name}, which is " +
                    "not an instance of ${returnTypeProjection}", null
        )
        return null
    }

    return returnValue as T
}

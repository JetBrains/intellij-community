// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:OptIn(ExperimentalStdlibApi::class)

package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.typeOf

internal inline fun <reified T> returnType(): TypeToken<T> = TypeToken.create()

internal inline fun <reified T> parameter(value: T?) = Parameter(TypeToken.create(), value)

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

interface ReflectionLogger {
    fun logIssue(message: String, exception: Throwable? = null)
}

fun ReflectionLogger(clazz: Class<*>) = ReflectionLogger(Logging.getLogger(clazz))

fun ReflectionLogger(logger: Logger): ReflectionLogger = GradleReflectionLogger(logger)

private class GradleReflectionLogger(private val logger: Logger) : ReflectionLogger {
    override fun logIssue(message: String, exception: Throwable?) {
        assert(false) { message }
        logger.error("[Reflection Error] $message", exception)
    }
}

internal class Parameter<T>(val typeToken: TypeToken<T>, val value: T?)

internal val Parameter<*>.jvmErasure: Class<*>
    get() {
        if (typeToken.type.isMarkedNullable) {
            return typeToken.type.jvmErasure.javaObjectType
        }
        return typeToken.type.jvmErasure.java
    }

internal class ParameterList(private val parameters: List<Parameter<*>>) : List<Parameter<*>> by parameters {
    companion object {
        val empty = ParameterList(emptyList())
    }
}

internal inline fun <reified T> Any.callReflective(
    methodName: String, params: ParameterList, returnTypeProjection: TypeToken<T>, logger: ReflectionLogger
): T? {
    val method = try {
        this::class.java.getMethod(methodName, *params.map { it.jvmErasure }.toTypedArray())
    } catch (e: Exception) {
        logger.logIssue("Failed to invoke $methodName on ${this.javaClass.name}", e)
        return null
    }
    method.trySetAccessible()

    val returnValue = try {
        method.invoke(this, *params.map { it.value }.toTypedArray())
    } catch (t: Throwable) {
        logger.logIssue("Failed to invoke $methodName on ${this.javaClass.name}", t)
        return null
    }

    if (returnValue == null) {
        if (!returnTypeProjection.type.isMarkedNullable) {
            logger.logIssue("Method $methodName on ${this.javaClass.name} unexpectedly returned null (expected $returnTypeProjection)")
        }
        return null
    }

    if (!returnTypeProjection.type.jvmErasure.isInstance(returnValue)) {
        logger.logIssue(
            "Method $methodName on ${this.javaClass.name} unexpectedly returned ${returnValue.javaClass.name}, which is " +
                    "not an instance of ${returnTypeProjection}"
        )
        return null
    }

    return returnValue as T
}

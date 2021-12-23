// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:OptIn(ExperimentalStdlibApi::class)

package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

internal inline fun <reified T> returnType(): TypeToken<T> = TypeToken.create()

internal inline fun <reified T> parameter(value: T?): Parameter<T> = Parameter(TypeToken.create(), value)

internal fun parameters(vararg parameter: Parameter<*>): ParameterList =
    if (parameter.isEmpty()) ParameterList.empty else ParameterList(parameter.toList())

internal class TypeToken<T> private constructor(
    val kotlinType: KType?,
    val kotlinClass: KClass<*>,
    val isMarkedNullable: Boolean
) {
    override fun toString(): String {
        return kotlinType?.toString() ?: (kotlinClass.java.name + if (isMarkedNullable) "?" else "")
    }

    companion object {
        inline fun <reified T> create(): TypeToken<T> {
            /* KType is not supported with older versions of Kotlin bundled in old Gradle versions (4.x) */
            val type = runCatching { typeOf<T>() }.getOrNull()

            val isMarkedNullable = when {
                type != null -> type.isMarkedNullable
                else -> null is T
            }

            return TypeToken(type, T::class, isMarkedNullable)
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

internal class Parameter<T>(
    /**
     * Optional, because this is not supported in old Gradle/Kotlin Versions
     */
    val typeToken: TypeToken<T>,
    val value: T?
)

internal class ParameterList(private val parameters: List<Parameter<*>>) : List<Parameter<*>> by parameters {
    companion object {
        val empty = ParameterList(emptyList())
    }
}

internal inline fun <reified T> Any.callReflective(
    methodName: String, parameters: ParameterList, returnTypeToken: TypeToken<T>, logger: ReflectionLogger
): T? {
    val parameterClasses = parameters.map { parameter ->
        /* Ensure using the object representation for primitive types, when marked nullable */
        if (parameter.typeToken.isMarkedNullable) parameter.typeToken.kotlinClass.javaObjectType
        else parameter.typeToken.kotlinClass.javaPrimitiveType ?: parameter.typeToken.kotlinClass.java
    }

    val method = try {
        this::class.java.getMethod(methodName, *parameterClasses.toTypedArray())
    } catch (e: Exception) {
        logger.logIssue("Failed to invoke $methodName on ${this.javaClass.name}", e)
        return null
    }

    runCatching {
        @Suppress("Since15")
        method.trySetAccessible()
    }

    val returnValue = try {
        method.invoke(this, *parameters.map { it.value }.toTypedArray())
    } catch (t: Throwable) {
        logger.logIssue("Failed to invoke $methodName on ${this.javaClass.name}", t)
        return null
    }

    if (returnValue == null) {
        if (!returnTypeToken.isMarkedNullable) {
            logger.logIssue("Method $methodName on ${this.javaClass.name} unexpectedly returned null (expected $returnTypeToken)")
        }
        return null
    }

    if (!returnTypeToken.kotlinClass.javaObjectType.isInstance(returnValue)) {
        logger.logIssue(
            "Method $methodName on ${this.javaClass.name} unexpectedly returned ${returnValue.javaClass.name}, which is " +
                    "not an instance of ${returnTypeToken}"
        )
        return null
    }

    return returnValue as T
}

internal fun Any.callReflectiveAnyGetter(methodName: String, logger: ReflectionLogger): Any? = callReflectiveGetter<Any>(methodName, logger)

internal inline fun <reified T> Any.callReflectiveGetter(methodName: String, logger: ReflectionLogger): T? =
    callReflective(methodName, parameters(), returnType<T>(), logger)
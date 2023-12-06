// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics.compilationError

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm
import java.lang.reflect.Field
import java.lang.reflect.Modifier

class KotlinCompilationErrorIdValidationRule : CustomValidationRule() {
    override fun getRuleId(): String = "kotlin.compilation.error.id"
    override fun doValidate(data: String, context: EventContext): ValidationResultType =
        if (allowedCompilationErrorsIds.contains(data)) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
}

private val allowedCompilationErrorsIds: List<String> =
    listOf(Errors::class.java, ErrorsJvm::class.java).flatMap { clazz ->
        clazz.fields.filter { Modifier.isStatic(it.modifiers) }.map(Field::getName)
    }

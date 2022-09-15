// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.diagnostic

import com.intellij.diagnostic.KotlinCompilerCrash
import java.util.regex.Pattern

class CompilerInternalError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    companion object {
        private val STACK_TRACE_ELEMENT_PATTERN = Pattern.compile("at (.+)\\.(.+)\\((.+)(:)*(\\d*)\\)$")
        private val CAUSED_BY_ELEMENT_PATTERN = Pattern.compile("Caused by: (.+):(.+)$")
        private const val CAUSED_BY_PREFIX = "Caused by:"


        private fun parseStackTraceLine(line: String): StackTraceElement? {
            val matcher = STACK_TRACE_ELEMENT_PATTERN.matcher(line.trim())
            if (matcher.matches()) {
                val declaringClass = matcher.group(1)
                val methodName = matcher.group(2)
                val fileName = matcher.group(3)
                val lineNumberString = matcher.group(5)
                //for native methods no line numbers
                val lineNumber = if (lineNumberString.isBlank()) 0 else Integer.parseInt(lineNumberString)

                return StackTraceElement(declaringClass, methodName, fileName, lineNumber)
            }
            //Native methods will be filtered as they don't have lineNumber
            return null
        }

        private fun parseCausedByLine(line: String): Exception? {
            val matcher = CAUSED_BY_ELEMENT_PATTERN.matcher(line.trim())
            if (matcher.matches()) {
                val declaringClass = matcher.group(1)
                val message = matcher.group(2)

                return KotlinCompilerCrash("declaringClass: message")
            }
            //Native methods will be filtered as they don't have lineNumber
            return null
        }

        fun parseStack(rawStack: List<String>): List<Throwable> {
            var currentMessage: String? = null
            var currentStack = ArrayList<StackTraceElement>()
            var currentReasonsList = ArrayList<Pair<String, List<StackTraceElement>>>()
            val exceptions = ArrayList<Throwable>()

            fun clearValues() {
                currentMessage = null
                currentStack = ArrayList()
                currentReasonsList = ArrayList()
            }

            clearValues()
            rawStack.forEach {
                when {
                    it.isEmpty() -> {} //skip empty line
                    it.trim().startsWith("...") -> {} //ignore cut stacktrace
                    it.trim().startsWith("Attachments:") -> {} //ignore
                    it.trim().startsWith("---") -> {
                        exceptions.add(joinIntoException(currentMessage, currentStack, currentReasonsList))
                        clearValues()
                    }

                    it.trim().startsWith("at") -> parseStackTraceLine(it.trim())?.also { currentStack.add(it) }
                    it.trim().startsWith(CAUSED_BY_PREFIX) -> {
                        currentReasonsList.add(Pair(currentMessage ?: "TODO", currentStack))
                        currentMessage = it.trim().substring(CAUSED_BY_PREFIX.length)
                        currentStack = ArrayList()
                    }
                    else -> currentMessage = currentMessage?.let{ message -> "$message ${it.trim()}" } ?: it.trim()

                }
            }
            exceptions.add(joinIntoException(currentMessage, currentStack, currentReasonsList))
            return exceptions
        }

        private fun parseStackWithoutAttachments(rawStack: List<String>): List<Throwable> {
            var message: String? = null
            var currentStack = ArrayList<StackTraceElement>()
            val reasonsList = ArrayList<Pair<String, List<StackTraceElement>>>()
            rawStack.forEach {
                when {
                    it.isEmpty() -> {} //skip empty line, don't expect 2 or more exceptions in the log
                    it.trim().startsWith("...") -> {} //ignore cut stacktrace
                    message == null -> message = it
                    it.trim().startsWith("at") -> parseStackTraceLine(it.trim())?.also { currentStack.add(it) }
                    it.trim().startsWith(CAUSED_BY_PREFIX) -> {
                        reasonsList.add(Pair(message ?: "TODO", currentStack))
                        message = it.trim().substring(CAUSED_BY_PREFIX.length)
                        currentStack = ArrayList()
                    }
                }
            }
            return listOf(joinIntoException(message, currentStack, reasonsList))
        }

        private fun joinIntoException(
            message: String?,
            currentStack: ArrayList<StackTraceElement>,
            reasonsList: ArrayList<Pair<String, List<StackTraceElement>>>
        ): CompilerInternalError {
            var exception = CompilerInternalError(message ?: "TODO").also { it.stackTrace = currentStack.toTypedArray() }

            reasonsList.asReversed().forEach {
                exception = CompilerInternalError(it.first, exception).also { it.stackTrace = currentStack.toTypedArray() }
            }
            return exception
        }

        //init {
        //    val stElements = ArrayList<StackTraceElement>()
        //    val attachments = ArrayList<Pair<String, Throwable>>()
        //
        //    cause?.also { stElements.add(StackTraceElement(it.split(":")[0], "<init>", null, 0)) }
        //    var currentStack = ArrayList<StackTraceElement>()
        //    var currentMessage: String? = null
        //    var withAttachments = false
        //    var attachmentKey: String? = null
        //    rawStack.forEach {
        //        when {
        //            it.trim().startsWith("...") -> {} //ignore cut stacktrace
        //            it.trim().startsWith("Attachments:") -> {
        //                withAttachments = true
        //            } //indicates several causes
        //            it.trim().startsWith("---") -> attachments.add(
        //                Pair(
        //                    attachmentKey ?: "DEFAULT_EXCEPTION_KEY",
        //                    KotlinCompilerCrash(currentMessage ?: "TODO", cause = null)
        //                )
        //            )
        //
        //            it.trim().startsWith("at") -> parseStackTraceLine(it.trim())?.also { currentStack.add(it) }
        //            it.trim().startsWith("Caused by:") -> parseCausedByLine(it)?.also { attachments.add(it) }
        //            withAttachments == true && attachmentKey == null -> {
        //                attachmentKey = it.trim()
        //            }
        //
        //            withAttachments == true && attachmentKey != null -> {
        //                currentMessage = it.trim()
        //            }
        //
        //            else -> {
        //                currentMessage = it.trim()
        //            }
        //        }
        //
        //        //val clazzWithMethod = it.trim().split("(")[0]
        //        //val clazz = clazzWithMethod.substring(3, clazzWithMethod.lastIndexOf('.'))
        //        //val method = clazzWithMethod.substring(clazzWithMethod.lastIndexOf('.') + 1)
        //        //
        //        //val file = it.trim().split("(")[1].split(":")[0]
        //        //val line = it.trim().split("(")[1].split(":")?.getOrNull(1)?.split(")")?.get(0)?.toIntOrNull() ?: 0
        //        //StackTraceElement(clazz, method, file, line)
        //
        //    }.toList())
        //    super.setStackTrace(stElements.toTypedArray())
        //}

    }
}
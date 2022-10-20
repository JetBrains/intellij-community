// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.test

import com.google.common.xml.XmlEscapers
import com.intellij.openapi.util.text.StringUtil
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.util.io.exists
import com.intellij.util.io.readText
import junit.framework.TestCase
import org.jetbrains.kotlin.test.util.trimTrailingWhitespacesAndAddNewlineAtEOF
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.writeText

object KotlinTestHelpers {
    fun getExpectedPath(path: Path, suffix: String): Path {
        val parent = path.parent

        val nameWithoutExtension = path.nameWithoutExtension
        val extension = path.extension

        if (extension.isEmpty()) {
            return parent.resolve(nameWithoutExtension + suffix)
        } else {
            return parent.resolve("$nameWithoutExtension$suffix.$extension")
        }
    }

    fun getTestRootPath(testClass: Class<*>): Path {
        var current = testClass
        while (true) {
            // @TestRoot should be defined on a top-level class
            current = current.enclosingClass ?: break
        }

        val testRootAnnotation = current.getAnnotation(TestRoot::class.java)
            ?: throw AssertionError("@${TestRoot::class.java.name} annotation must be defined on a class '${current.name}'")

        return KotlinRoot.PATH.resolve(testRootAnnotation.value)
    }

    fun assertEqualsToPath(expectedPath: Path, actual: String) {
        assertEqualsToPath(expectedPath, actual, { it }) { "Expected file content differs from the actual result" }
    }

    fun assertEqualsToPath(expectedPath: Path, actual: String, sanitizer: (String) -> String, message: () -> String) {
        if (!expectedPath.exists()) {
            expectedPath.writeText(actual)
            TestCase.fail("File didn't exist. New file was created (${expectedPath.absolutePathString()}).")
        }

        fun process(output: String): String {
            return output
                .trim()
                .let(StringUtil::convertLineSeparators)
                .trimTrailingWhitespacesAndAddNewlineAtEOF()
                .let(sanitizer)
        }

        val processedExpected = process(expectedPath.readText())
        val processedActual = process(actual)
        if (processedExpected != processedActual) {
            throw FileComparisonFailure(message(), processedExpected, processedActual, expectedPath.absolutePathString())
        }
    }

    fun insertTags(text: String, tags: List<Tag>): String {
        val sortedTags = tags.sortedByDescending { it.startOffset }
        check(sortedTags.zipWithNext().all { it.first.startOffset > it.second.endOffset }) { "Overlapping tags are not supported" }

        return sortedTags
            .fold(text) { current, tag ->
                current.substring(0, tag.startOffset) +
                        tag.renderOpen() +
                        current.substring(tag.startOffset, tag.endOffset) +
                        tag.renderClose() +
                        current.substring(tag.endOffset)
            }
    }

    fun stripTags(text: String, vararg tagNames: String): String {
        val tagNamesPattern = tagNames.joinToString("|", prefix = "(", postfix = ")") { "(?:" + Regex.escape(it) + ")" }
        return Regex("</?$tagNamesPattern.*?>").replace(text, "")
    }
}

data class Tag(val startOffset: Int, val endOffset: Int, val name: String, val arguments: Map<String, String>) {
    constructor(startOffset: Int, endOffset: Int, name: String, vararg arguments: Pair<String, String>)
            : this(startOffset, endOffset, name, arguments.toMap())

    fun renderOpen(): String = buildString {
        append('<').append(name)
        for ((key, value) in arguments) {
            val escapedValue = value
                .let { KotlinTestHelpers.stripTags(it, "html") }
                .let(XmlEscapers.xmlAttributeEscaper()::escape)
                .replace("&apos;", "'")
                .replace("&quot;", "''")
                .replace("&amp;", "&")

            append(' ').append(key).append("=")
            append('"').append(escapedValue).append('"')
        }
        append(">")
    }

    fun renderClose(): String = buildString {
        append("</").append(name).append('>')
    }
}
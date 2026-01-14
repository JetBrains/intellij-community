// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle.completion

import com.intellij.util.text.matching.MatchedFragment
import org.jetbrains.plugins.gradle.completion.GradleDependencyCompletionMatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class GradleDependencyCompletionMatcherTest {
    @ParameterizedTest
    @CsvSource(
        "'juni', 'implementation(\"junit:junit:4.13.2\")', true",
        "'org:missing', 'implementation(\"org.junit.jupiter:junit-jupiter-api:5.10.2\")', false",
        "'junit:jupiter', 'implementation(\"org.junit.jupiter:junit-jupiter-api:5.10.2\")', true",
        "'google:gson', 'implementation(\"com.google.code.gson:gson:1.7.2\")', true",
    )
    fun `test prefixMatches`(prefix: String, name: String, expected: Boolean) {
        val matcher = GradleDependencyCompletionMatcher(prefix)
        val result = matcher.prefixMatches(name)
        assertEquals(
            expected,
            result,
            "prefixMatches: prefix='$prefix', name='$name' expected $expected but was $result"
        )
    }

    @ParameterizedTest
    @CsvSource(
        // simple name without parentheses
        "'core', 'org.example:{core}-utils:1.0'",
        // no match when only configuration name contains prefix -> empty list
        "'impl', 'implementation(\"org.example:lib:1.0\")'",
        // no occurrence at all -> empty list
        "'missing', 'org.sample:artifact:1.0'",
        // match inside an artifact section after parenthesis -> expected fragment marked with {}
        "'implement', 'implementation(\"org.example:lib-{implement}ation:1.0\")'",
        // substring + prefix
        "'google:gson', 'implementation(\"com.{google}.code.gson:{gson}:1.7.2\")'",
        "'google:gson', 'implementation(\"com.google.co…'",
        // substring + substring
        "'springfra:actu', 'implementation(\"org.{springfra}mework.boot:spring-boot-{actu}ator:3.1.3\")'"
    )
    fun `test getMatchingFragments`(prefix: String, annotatedName: String) {
        // Parse annotatedName to compute clean name (without braces)
        fun parseAnnotated(spec: String): String {
            val sb = StringBuilder()
            var i = 0
            while (i < spec.length) {
                val ch = spec[i]
                if (ch == '{') {
                    val endBrace = spec.indexOf('}', i + 1)
                    require(endBrace >= 0) { "Unmatched '{' in: $spec" }
                    val inner = spec.substring(i + 1, endBrace)
                    sb.append(inner)
                    i = endBrace + 1
                } else {
                    sb.append(ch)
                    i++
                }
            }
            return sb.toString()
        }

        // Apply ranges as braces to the clean name to build an annotated string
        fun annotate(name: String, ranges: List<MatchedFragment>): String {
            if (ranges.isEmpty()) return name
            val sorted = ranges.sortedBy { it.startOffset }
            val starts = sorted.groupBy { it.startOffset }
            val ends = sorted.groupBy { it.endOffset }
            val out = StringBuilder()
            for (i in 0..name.length) {
                // close any ranges ending at i
                ends[i]?.forEach { _ -> out.append('}') }
                if (i == name.length) break
                // open any ranges starting at i
                starts[i]?.forEach { _ -> out.append('{') }
                out.append(name[i])
            }
            return out.toString()
        }

        val name = parseAnnotated(annotatedName)

        val matcher = GradleDependencyCompletionMatcher(prefix)
        val fragments = matcher.getMatchingFragments(prefix, name)

        val actualAnnotated = annotate(name, fragments)
        val message = "getMatchingFragments: prefix='$prefix' expected $annotatedName but was $actualAnnotated"
        assertEquals(annotatedName, actualAnnotated, message)
    }
}

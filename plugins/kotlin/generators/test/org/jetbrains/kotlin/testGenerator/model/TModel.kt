// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.testGenerator.model

import org.jetbrains.kotlin.test.TargetBackend
import java.io.File

typealias ModelMatcher = (String) -> ModelMatchingResult?
class ModelMatchingResult(val methodName: String)

data class TModel(
    val path: String,
    val matcher: ModelMatcher,
    val testClassName: String,
    val testMethodName: String,
    val flatten: Boolean,
    val targetBackend: TargetBackend,
    val excludedDirectories: List<String>,
    val depth: Int
)

fun ModelMatcher.withPrecondition(precondition: (String) -> Boolean): ModelMatcher {
    return { name -> if (precondition(name)) this(name) else null }
}

object Patterns {
    fun forRegex(regex: String): ModelMatcher {
        return f@ { name ->
            val result = regex.toRegex().matchEntire(name) ?: return@f null
            return@f ModelMatchingResult(result.groupValues[1])
        }
    }

    private fun forExtension(extension: String): ModelMatcher {
        val escapedExtension = Regex.escapeReplacement(extension)
        return forRegex("^(.+)\\.$escapedExtension\$")
    }

    val DIRECTORY: ModelMatcher = forRegex("^([^\\.]+)$")

    val TEST: ModelMatcher = forExtension("test")
    val KT: ModelMatcher = forExtension("kt")
    val TXT: ModelMatcher = forExtension("txt")
    val KTS: ModelMatcher = forExtension("kts")
    val JAVA: ModelMatcher = forExtension("java")
    val WS_KTS: ModelMatcher = forExtension("ws.kts")

    val KT_OR_KTS: ModelMatcher = forRegex("^(.+)\\.(kt|kts)$")
    val KT_WITHOUT_DOTS: ModelMatcher = forRegex("^([^.]+)\\.kt$")
    val KT_OR_KTS_WITHOUT_DOTS: ModelMatcher = forRegex("^([^.]+)\\.(kt|kts)$")
    val KT_WITHOUT_FIR_PREFIX: ModelMatcher = forRegex("""^(.+)(?<!\.fir)\.kt$""")
}

fun MutableTSuite.model(
    path: String,
    pattern: ModelMatcher = Patterns.KT,
    isRecursive: Boolean = true,
    testClassName: String = File(path).toJavaIdentifier().capitalize(),
    testMethodName: String = "doTest",
    flatten: Boolean = false,
    targetBackend: TargetBackend = TargetBackend.ANY,
    excludedDirectories: List<String> = emptyList(),
    depth: Int = Int.MAX_VALUE
) {
    models += TModel(
        path, pattern, testClassName, testMethodName,
        flatten, targetBackend, excludedDirectories, if (!isRecursive) 0 else depth
    )
}

fun makeJavaIdentifier(text: String): String {
    return buildString {
        for (c in text) {
            append(if (Character.isJavaIdentifierPart(c)) c else "_")
        }
    }
}

fun File.toJavaIdentifier() = makeJavaIdentifier(name)
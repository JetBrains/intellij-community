// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.testGenerator.model

import org.jetbrains.kotlin.test.TargetBackend
import java.io.File

data class TModel(
    val path: String,
    val pattern: Regex,
    val testClassName: String,
    val testMethodName: String,
    val flatten: Boolean,
    val targetBackend: TargetBackend,
    val excludedDirectories: List<String>,
    val depth: Int,
    val testPerClass: Boolean,
    val bucketSize: Int?,
)

object Patterns {
    private fun forExtension(extension: String): Regex {
        val escapedExtension = Regex.escapeReplacement(extension)
        return "^(.+)\\.$escapedExtension\$".toRegex()
    }

    val DIRECTORY: Regex = "^([^.]+)$".toRegex()

    val TEST: Regex = forExtension("test")
    val KT: Regex = forExtension("kt")
    val TXT: Regex = forExtension("txt")
    val KTS: Regex = forExtension("kts")
    val JAVA: Regex = forExtension("java")
    val WS_KTS: Regex = forExtension("ws.kts")

    val KT_OR_KTS: Regex = "^(.+)\\.(kt|kts)$".toRegex()
    val KT_WITHOUT_DOTS: Regex = "^([^.]+)\\.kt$".toRegex()
    val KT_OR_KTS_WITHOUT_DOTS: Regex = "^([^.]+)\\.(kt|kts)$".toRegex()
}

fun MutableTSuite.model(
    path: String,
    pattern: Regex = Patterns.KT,
    isRecursive: Boolean = true,
    testClassName: String = File(path).toJavaIdentifier().capitalize(),
    testMethodName: String = "doTest",
    flatten: Boolean = false,
    targetBackend: TargetBackend = TargetBackend.ANY,
    excludedDirectories: List<String> = emptyList(),
    depth: Int = Int.MAX_VALUE,
    testPerClass: Boolean = false,
    splitToBuckets: Boolean = true,
    bucketSize: Int = 20,
) {
    models += TModel(
        path = path,
        pattern = pattern,
        testClassName = testClassName,
        testMethodName = testMethodName,
        flatten = flatten,
        targetBackend = targetBackend,
        excludedDirectories = excludedDirectories,
        depth = if (!isRecursive) 0 else depth,
        testPerClass = testPerClass,
        bucketSize = if (!splitToBuckets) null else bucketSize,
    )
}

fun makeJavaIdentifier(text: String): String {
    return buildString {
        for (c in text) {
            append(if (Character.isJavaIdentifierPart(c)) c else "_")
        }
    }
}

fun File.toJavaIdentifier() = makeJavaIdentifier(nameWithoutExtension)
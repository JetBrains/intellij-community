// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.workspace

import org.jetbrains.kotlin.konan.target.HostManager

private enum class SanitationState {
    TAKE, TAKE_ON_THIS_HOST, EXCLUDE;
}

/**
 * Remove platform blocks that don't match the host, substitute KGP version placeholders.
 *
 * Platform blocks are declared as follows. Nested blocks are forbidden.
 * ```
 * #<HOST_NAME>
 *     /* ... */
 * #END
 * ```
 * `{{KGP_VERSION}}` stubs will be replaced by the KGP version used in a test
 */
internal fun sanitizeExpectedFile(text: String): String = buildString {
    var currentState = SanitationState.TAKE

    for ((index, line) in text.lines().withIndex()) {
        val nextState = nextState(currentState, line)

        if (nextState != currentState) {
            currentState = nextState
        } else {
            checkUnexpectedCommands(currentState, line, index + 1)
            appendLine(line)
        }
    }
}.trim()

private fun nextState(currentState: SanitationState, nextLine: String): SanitationState = when (currentState) {
    SanitationState.TAKE -> {
        val platformBlockMatch = platformDependentBlockPattern.matchEntire(nextLine)
        when {
            platformBlockMatch == null -> SanitationState.TAKE
            platformBlockMatch.groupValues.last() == getHostClassifier() -> SanitationState.TAKE_ON_THIS_HOST
            else -> SanitationState.EXCLUDE
        }
    }
    SanitationState.TAKE_ON_THIS_HOST -> when {
        blockEndPattern.matches(nextLine) -> SanitationState.TAKE
        else -> SanitationState.TAKE_ON_THIS_HOST
    }
    SanitationState.EXCLUDE -> when {
        blockEndPattern.matches(nextLine) -> SanitationState.TAKE
        else -> SanitationState.TAKE_ON_THIS_HOST
    }
}

private fun checkUnexpectedCommands(currentState: SanitationState, nextLine: String, lineNumber: Int) {
    when (currentState) {
        SanitationState.TAKE -> require(!blockEndPattern.matches(nextLine)) {
            "Line ${lineNumber}: unexpected end of block, no active block at this line."
        }
        SanitationState.TAKE_ON_THIS_HOST -> require(!platformDependentBlockPattern.matches(nextLine)) {
            "Line ${lineNumber}: unexpected new block, there's another active block at this line."
        }
        SanitationState.EXCLUDE -> require(!platformDependentBlockPattern.matches(nextLine)) {
            "Line ${lineNumber}: unexpected new block, there's another active block at this line."
        }
    }
}

private const val LINUX_HOST_CLASSIFIER = "LINUX"
private const val WINDOWS_HOST_CLASSIFIER = "WINDOWS"
private const val MACOS_HOST_CLASSIFIER = "MACOS"

private val hostAlternatives = listOf(LINUX_HOST_CLASSIFIER, MACOS_HOST_CLASSIFIER, WINDOWS_HOST_CLASSIFIER)

private val platformDependentBlockPattern = "^#\\s*(${hostAlternatives.joinToString("|")})\\s*$".toRegex()
private val blockEndPattern = "^#END\\s*$".toRegex()

private fun getHostClassifier() = when {
    HostManager.hostIsMingw -> WINDOWS_HOST_CLASSIFIER
    HostManager.hostIsMac -> MACOS_HOST_CLASSIFIER
    HostManager.hostIsLinux -> LINUX_HOST_CLASSIFIER
    else -> throw IllegalStateException("Unexpected host for tests: ${HostManager.hostName}")
}

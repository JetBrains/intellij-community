// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.test

import com.intellij.openapi.Disposable
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.util.regex.Pattern

private const val CHOSEN_OPTION_DIRECTIVE = "// CHOSEN_OPTION:"
private const val NO_OPTION_DIRECTIVE = "// NO_OPTION:"

/**
 * Register a [com.intellij.ui.ChooserInterceptor] that selects an option matching a regex pattern from a file directive.
 * The file should contain no more than one [CHOSEN_OPTION_DIRECTIVE] directive that should match exactly the expected result.
 * If the chosen option is present, an additional [NO_OPTION_DIRECTIVE] directive can be used to check against forbidden options.
 *
 * @return `true` if an interceptor has been registered
 */
fun registerDirectiveBasedChooserOptionInterceptor(fileText: String, testRootDisposable: Disposable): Boolean {
    val chosenPattern = checkAndPreparePattern(fileText, CHOSEN_OPTION_DIRECTIVE)
    val forbiddenPattern = checkAndPreparePattern(fileText, NO_OPTION_DIRECTIVE)
    if (chosenPattern == null) return false

    KotlinTestHelpers.registerChooserInterceptor(testRootDisposable) { options ->
        options.filter { forbiddenPattern?.matcher(it)?.matches() ?: false }.ifNotEmpty {
            throw AssertionError("Found forbidden options: ${this.joinToString(prefix = "[", postfix = "]")} (Pattern: $forbiddenPattern)")
        }
        val matching = options.filter { chosenPattern.matcher(it).matches() }
        if (matching.size != 1) {
            throw AssertionError(
                "Expected single matching option (Pattern: $chosenPattern), but got: ${
                    matching.joinToString(
                        prefix = "[",
                        postfix = "]",
                    )
                }"
            )
        }
        matching.single()
    }
    return true
}

private fun checkAndPreparePattern(fileText: String, directive: String): Pattern? {
    val chosenOptions = InTextDirectivesUtils.findListWithPrefixes(fileText, directive)
    check(chosenOptions.size < 2) { "Multiple '$directive' options are not allowed" }
    if (chosenOptions.isEmpty()) return null
    val chosenOptionPatternString = chosenOptions.single().trim()
    return Pattern.compile(chosenOptionPatternString)
}

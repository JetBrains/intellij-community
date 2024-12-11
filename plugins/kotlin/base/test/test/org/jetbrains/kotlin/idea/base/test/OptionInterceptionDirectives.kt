// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.test

import com.intellij.openapi.Disposable
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

private const val CHOSEN_OPTION_DIRECTIVE = "// CHOSEN_OPTION:"
private const val NO_OPTION_DIRECTIVE = "// NO_OPTION:"

/**
 * Add an interceptor that selects a single option that contains the option from the [CHOSEN_OPTION_DIRECTIVE].
 *
 * @return `true` if the file contains the directive and the interceptor was registered
 */
fun registerDirectiveBasedChooserOptionInterceptor(fileText: String, testRootDisposable: Disposable): Boolean {
    val chosenOptions = InTextDirectivesUtils.findListWithPrefixes(fileText, CHOSEN_OPTION_DIRECTIVE)
    val forbiddenOptions = InTextDirectivesUtils.findListWithPrefixes(fileText, NO_OPTION_DIRECTIVE)
    check(chosenOptions.size < 2) { "Multiple chooser options are not allowed" }
    if (chosenOptions.isEmpty()) return false
    val chosenOption = chosenOptions.single().trim()
    KotlinTestHelpers.registerChooserInterceptor(testRootDisposable) { options ->
        forbiddenOptions.filter { options.contains(it) }.ifNotEmpty {
            throw AssertionError("Forbidden options found: ${this.joinToString()}")
        }
        options.singleOrNull { it == chosenOption } ?: throw AssertionError("Option not found: $chosenOption")
    }
    return true
}

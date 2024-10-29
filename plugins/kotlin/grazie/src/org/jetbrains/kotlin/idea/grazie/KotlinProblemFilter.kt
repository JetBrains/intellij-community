// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.grazie

import com.intellij.grazie.text.ProblemFilter
import com.intellij.grazie.text.TextContent.TextDomain.DOCUMENTATION
import com.intellij.grazie.text.TextProblem
import com.intellij.grazie.utils.ProblemFilterUtil
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag

class KotlinProblemFilter : ProblemFilter() {
    override fun shouldIgnore(problem: TextProblem): Boolean {
        val domain = problem.text.domain
        if (domain == DOCUMENTATION && problem.text.commonParent::class == KDocTag::class &&
            (ProblemFilterUtil.isUndecoratedSingleSentenceIssue(problem) || ProblemFilterUtil.isInitialCasingIssue(problem))) {
            return true
        }
        return false
    }
}
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.grazie

import com.intellij.grazie.text.ProblemFilter
import com.intellij.grazie.text.RuleGroup
import com.intellij.grazie.text.TextContent.TextDomain.DOCUMENTATION
import com.intellij.grazie.text.TextContent.TextDomain.LITERALS
import com.intellij.grazie.text.TextProblem
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag

class KotlinProblemFilter : ProblemFilter() {
    override fun shouldIgnore(problem: TextProblem): Boolean {
        val domain = problem.text.domain
        if (domain == LITERALS) {
            return problem.fitsGroup(RuleGroup.LITERALS)
        }
        if (domain == DOCUMENTATION && problem.text.commonParent::class == KDocTag::class) {
            return problem.fitsGroup(RuleGroup.UNDECORATED_SINGLE_SENTENCE)
        }
        return false
    }
}
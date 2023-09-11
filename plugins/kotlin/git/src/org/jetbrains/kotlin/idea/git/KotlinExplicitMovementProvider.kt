// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Couple
import com.intellij.openapi.vcs.FilePath
import git4idea.checkin.GitCheckinExplicitMovementProvider
import org.jetbrains.kotlin.idea.base.codeInsight.pathBeforeJavaToKotlinConversion

class KotlinExplicitMovementProvider : GitCheckinExplicitMovementProvider() {
    override fun isEnabled(project: Project): Boolean {
        return true
    }

    override fun getDescription(): String {
        return KotlinGitBundle.message("j2k.extra.commit.description")
    }

    override fun getCommitMessage(oldCommitMessage: String): String {
        return KotlinGitBundle.message("j2k.extra.commit.commit.message")
    }

    override fun collectExplicitMovements(
        project: Project,
        beforePaths: List<FilePath>,
        afterPaths: List<FilePath>
    ): Collection<Movement> {
        val beforeHasJava = beforePaths.any { it.path.endsWith(".java") }
        val afterHasKotlin = afterPaths.any { it.path.endsWith(".kt") }
        if (!beforeHasJava || !afterHasKotlin) return emptyList()
        val movedChanges = ArrayList<Movement>()
        for (after in afterPaths) {
            val pathBeforeJ2K = after.virtualFile?.pathBeforeJavaToKotlinConversion ?: continue
            val before = beforePaths.firstOrNull { it.path == pathBeforeJ2K } ?: continue
            movedChanges.add(Movement(before, after))
        }
        // avoid processing huge changes
        if (beforePaths.size > 1000) return movedChanges
        val existing = movedChanges.toSet()
        val map = HashMap<String, FilePath>()
        for (before in beforePaths) {
            if (!before.path.endsWith(".java")) continue
            map[before.path.dropLast("java".length) + "kt"] = before
        }
        for (after in afterPaths) {
            val before = map[after.path] ?: continue
            val movement = Movement(before, after)
            if (existing.contains(movement)) continue
            movedChanges.add(movement)
        }
        return movedChanges
    }

    override fun afterMovementsCommitted(project: Project, movedPaths: MutableList<Couple<FilePath>>) {
        movedPaths.forEach { it.second.virtualFile?.pathBeforeJavaToKotlinConversion = null }
    }
}

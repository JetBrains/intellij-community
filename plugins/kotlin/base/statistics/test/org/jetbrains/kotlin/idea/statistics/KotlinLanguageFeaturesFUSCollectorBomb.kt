// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics

import com.intellij.idea.Bombed
import kotlin.test.Test

class KotlinLanguageFeaturesFUSCollectorBomb {

    @Bombed(
        year = 2023,
        month = 11,
        day = 15,
        user = "Frederik Haselmeier",
        description = """
        The KotlinLanguageFeaturesFUSCollector currently collects data about certain applied quick fixes, which is a custom solution.
        Instead, a more general solution for logging quick fixes should be developed for all quick fixes.
        See: https://youtrack.jetbrains.com/issue/KTIJ-25021
    """
    )
    @Test
    fun removeQuickFixFUSCollectionReminderBomb() {
        assert(false)
    }
}
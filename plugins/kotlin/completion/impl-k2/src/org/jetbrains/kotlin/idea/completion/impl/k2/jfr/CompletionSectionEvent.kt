// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.jfr

import jdk.jfr.Category
import jdk.jfr.Enabled
import jdk.jfr.Event
import jdk.jfr.Label
import jdk.jfr.Name
import jdk.jfr.StackTrace

@Category("Kotlin", "Code Completion")
@Name("org.jetbrains.kotlin.idea.completion.impl.k2.jfr.CompletionSectionEvent")
@Label("Completion Section")
@StackTrace(false)
@Enabled(false)
internal class CompletionSectionEvent(
    @Label("Contributor Name")
    val contributorName: String = "",
    @Label("Section Name")
    val sectionName: String? = null,
) : Event() {
    @Label("Was Completed")
    var wasCompleted: Boolean = false
}
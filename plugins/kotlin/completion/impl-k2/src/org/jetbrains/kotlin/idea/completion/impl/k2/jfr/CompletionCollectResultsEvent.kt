// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.jfr

import jdk.jfr.Category
import jdk.jfr.Label
import jdk.jfr.Name
import jdk.jfr.StackTrace

@Category("Kotlin", "Code Completion")
@Name("org.jetbrains.kotlin.idea.completion.impl.k2.jfr.CompletionCollectResultsEvent")
@Label("Completion Collect Results")
@StackTrace(false)
internal class CompletionCollectResultsEvent(
    @Label("Contributor Name")
    val contributorName: String = "",
    @Label("Section Name")
    val sectionName: String? = null,
) : AbstractCompletionEvent() {
    @Label("Was Interrupted")
    override var wasInterrupted: Boolean = false

    @Label("Consumed Elements")
    var consumedElements: Int = 0
}

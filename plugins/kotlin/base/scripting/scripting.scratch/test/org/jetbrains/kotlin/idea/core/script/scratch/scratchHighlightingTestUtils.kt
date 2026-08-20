// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.scratch

import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntityProvider
import org.jetbrains.kotlin.idea.highlighter.ALLOW_ERRORS
import org.jetbrains.kotlin.idea.highlighter.CHECK_SYMBOL_NAMES
import org.jetbrains.kotlin.idea.highlighter.checkHighlighting
import org.jetbrains.kotlin.idea.test.Directives
import java.io.File

internal fun KotlinScratchFile.checkMetaInfoHighlighting(goldenFileName: String, allowErrors: Boolean = false) {
    val psiFile = checkNotNull(getPsiFile()) { "no PSI file for the scratch: $virtualFile" }

    val directives = Directives().apply {
        put(CHECK_SYMBOL_NAMES, null)
        put(HIGHLIGHTER_ATTRIBUTES_KEY, null)
        if (allowErrors) put(ALLOW_ERRORS, null)
    }

    checkHighlighting(psiFile, File(SCRATCH_HIGHLIGHTING_TEST_DATA, goldenFileName), directives, project)
}

internal fun KotlinScratchFile.awaitScriptEntity() {
    PlatformTestUtil.waitWithEventsDispatching(
        "the scratch never got a KotlinScriptEntity, so highlighting would not reflect its configuration",
        { KotlinScriptEntityProvider.provide(project, virtualFile) != null },
        SCRIPT_ENTITY_TIMEOUT_SECONDS,
    )
}

internal const val SCRIPT_ENTITY_TIMEOUT_SECONDS: Int = 30

private const val HIGHLIGHTER_ATTRIBUTES_KEY = "HIGHLIGHTER_ATTRIBUTES_KEY"

private val SCRATCH_HIGHLIGHTING_TEST_DATA: File =
    KotlinRoot.DIR.resolve("base/scripting/scripting.scratch/testData/highlighting")

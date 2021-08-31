// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.project.test.base

import org.jetbrains.kotlin.idea.perf.common.HighlightFile
import org.jetbrains.kotlin.idea.perf.common.ProjectAction
import org.jetbrains.kotlin.idea.perf.common.ProjectData
import org.jetbrains.kotlin.idea.perf.common.TypeAndAutocompleteInFile
import org.jetbrains.kotlin.idea.testFramework.ProjectOpenAction

internal object RustProject {
    /*
      The fork of intellij-rust plugin is considered here https://github.com/darthorimar/intellij-rust
      It has required code pregenerated manually
      Also, it is always the same version of rust plugin codebase to test against
    */
    val project = ProjectData(
        name = "intellijRustPlugin",
        path = "../intellij-rust",
        openAction = ProjectOpenAction.GRADLE_PROJECT,
    )

    val actions: List<ProjectAction> = run {
        val basePath = "src/main/kotlin/org/rust"

        listOf(
            HighlightFile("$basePath/ide/inspections/RsExternalLinterInspection.kt"),
            HighlightFile("$basePath/ide/injected/RsDoctestLanguageInjector.kt"),
            HighlightFile("$basePath/cargo/runconfig/filters/RegexpFileLinkFilter.kt"),
            HighlightFile("$basePath/cargo/util/CargoOptions.kt"),
            HighlightFile("$basePath/lang/core/macros/MacroExpansionManager.kt"),
            HighlightFile("$basePath/lang/core/resolve/NameResolution.kt"),

            TypeAndAutocompleteInFile(
                filePath = "$basePath/cargo/runconfig/filters/RegexpFileLinkFilter.kt",
                typeAfter = "fun applyFilter(line: String, entireLength: Int): Filter.Result? {",
                textToType = "val a = l",
                expectedLookupElements = listOf("line"),
                note = "in-method completion",
            ),

            // TODO fix, fails in UAST type mapping
            /* TypeAndAutocompleteInFile(
                 filePath =  "$basePath/lang/core/resolve/NameResolution.kt",
                 typeAfter = "private data class ImplicitStdlibCrate(val name: String, val crateRoot: RsFile)",
                 textToType = "\nval a = ",
                 expectedLookupElements = listOf("processAssocTypeVariants"),
                 note = "top-level completion",
             ),

             TypeAndAutocompleteInFile(
                 filePath =  "$basePath/lang/core/resolve/NameResolution.kt",
                 typeAfter = "testAssert { cameFrom.context == scope }",
                 textToType = "\nval a = s",
                 expectedLookupElements = listOf("scope"),
                 note = "in big method in big file completion",
             ),*/
        )
    }
}
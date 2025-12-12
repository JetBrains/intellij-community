// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.test.utils.withExtension
import java.io.File
import kotlin.test.assertTrue

internal fun KtCodeFragment.checkImports(testFile: File) {
    val importList = importsAsImportList()
    val importsText = StringUtil.convertLineSeparators(importList?.text ?: "")

    val importsAfterFile = testFile.withExtension(".kt.after.imports")
    if (importsAfterFile.exists()) {
        KotlinTestUtils.assertEqualsToFile(importsAfterFile, importsText)
    } else {
        assertTrue(importsText.isEmpty(), "Unexpected imports found: $importsText")
    }
}

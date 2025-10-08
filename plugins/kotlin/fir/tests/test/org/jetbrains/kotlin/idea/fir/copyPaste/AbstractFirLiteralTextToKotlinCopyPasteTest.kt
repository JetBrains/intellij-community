// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.copyPaste

import org.jetbrains.kotlin.idea.conversion.copy.AbstractLiteralTextToKotlinCopyPasteTest
import java.io.File

abstract class AbstractFirLiteralTextToKotlinCopyPasteTest : AbstractLiteralTextToKotlinCopyPasteTest() {
    override fun getExpectedFile(testFile: File): File {
        return File(testFile.parent, testFile.nameWithoutExtension + ".expected.k2.kt").takeIf { it.exists() }
            ?: super.getExpectedFile(testFile)
    }
}

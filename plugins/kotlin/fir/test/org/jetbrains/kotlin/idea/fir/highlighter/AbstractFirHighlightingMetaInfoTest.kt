// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.highlighter

import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightingMetaInfoTest
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.test.utils.IgnoreTests

abstract class AbstractFirHighlightingMetaInfoTest : AbstractHighlightingMetaInfoTest() {
    override fun isFirPlugin(): Boolean = true

    override fun getDefaultProjectDescriptor() = ProjectDescriptorWithStdlibSources.INSTANCE

    override fun doTest(unused: String) {
      IgnoreTests.runTestIfNotDisabledByFileDirective(
          testDataFile().toPath(),
          disableTestDirective = IgnoreTests.DIRECTIVES.IGNORE_FIR,
          additionalFilesExtensions = arrayOf(HIGHLIGHTING_EXTENSION)
      ) {
        // warnings are not supported yet
        super.doTest(unused)
      }
    }
}
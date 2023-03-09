// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import java.io.File

abstract class AbstractK1IntentionTest2 : AbstractK1IntentionTest() {
    override fun intentionFileName() = ".intention2"
    override fun afterFileNameSuffix(ktFilePath: File) = ".after2"
    override fun intentionTextDirectiveName() = "INTENTION_TEXT_2"
    override fun isApplicableDirectiveName() = "IS_APPLICABLE_2"
}

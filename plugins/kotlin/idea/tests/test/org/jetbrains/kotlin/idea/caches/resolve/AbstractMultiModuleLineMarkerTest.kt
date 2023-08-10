// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.resolve

import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.codeMetaInfo.AbstractLineMarkerCodeMetaInfoTest
import java.io.File

abstract class AbstractMultiModuleLineMarkerTest : AbstractLineMarkerCodeMetaInfoTest() {
    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve("multiModuleLineMarker")
}

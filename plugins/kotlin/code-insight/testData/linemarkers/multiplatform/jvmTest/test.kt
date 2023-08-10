// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package sample

import kotlin.test.Test
import kotlin.test.assertTrue

class <!LINE_MARKER("descr='Run Test'")!>SampleTestsJVM<!> {
    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>testHello<!>() {
        assertTrue("JVM" in hello())
    }
}
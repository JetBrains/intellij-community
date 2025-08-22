//region Test configuration
// - hidden: line markers
//endregion
package org.example.repro

import org.junit.jupiter.api.Test

class ReproTest {

    @Test
    fun test() {
        "Hello World!".<!HIGHLIGHTING("severity='ERROR'; descr='[INVISIBLE_REFERENCE] Cannot access 'fun String.repro(): String': it is internal in file.'")!>repro<!>()
    }
}

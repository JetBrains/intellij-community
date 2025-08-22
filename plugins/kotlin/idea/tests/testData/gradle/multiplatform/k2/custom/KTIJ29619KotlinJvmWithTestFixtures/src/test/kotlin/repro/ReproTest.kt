//region Test configuration
// - hidden: line markers
//endregion
package org.example.repro

import org.example.internalMainProperty
import org.junit.jupiter.api.Test

class ReproTest {

    @Test
    fun test() {
        println(internalMainProperty)
        println(internalTestFixtureProperty)
    }
}

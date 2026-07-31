// PROBLEM: Arguments to 'assertEquals()' are in wrong order
// FIX: Flip compared arguments

// WITH_STDLIB

package sample

import junit.framework.TestCase

class DeviceInfoTest : TestCase() {
    fun testDeviceInfo(actual: List<Int>) {
        val one = 1
        <caret>assertEquals(actual, listOf(one, one))
    }
}

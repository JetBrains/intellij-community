// PROBLEM: Arguments to 'assertEquals()' are in wrong order
// FIX: Flip compared arguments
// IGNORE_K1
// WITH_STDLIB

package sample

import junit.framework.TestCase

class DeviceInfoTest : TestCase() {
    fun testDeviceInfo(info: DeviceInfo) {
        <caret>assertEquals(info.characteristicsList, listOf("emulator", "watch"))
    }
}

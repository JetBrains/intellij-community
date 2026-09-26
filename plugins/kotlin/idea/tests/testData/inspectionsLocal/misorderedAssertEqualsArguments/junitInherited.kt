// PROBLEM: Arguments to 'assertEquals()' are in wrong order
// FIX: Flip compared arguments

// WITH_STDLIB

package sample

import junit.framework.TestCase

class DeviceInfoTest : TestCase() {
    fun testDeviceInfo(info: DeviceInfo) {
        <caret>assertEquals(info.buildTags, "release-keys")
    }
}

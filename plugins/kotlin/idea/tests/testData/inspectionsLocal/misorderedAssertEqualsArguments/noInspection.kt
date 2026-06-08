// PROBLEM: none

// WITH_STDLIB

package sample

import junit.framework.TestCase
import org.testng.Assert

class DeviceInfoTest : TestCase() {
    fun testDeviceInfo(info: DeviceInfo, expected: String, actual: String) {
        <caret>assertEquals("release-keys", info.buildTags)
        assertEquals(expected, actual)
        assertEquals(expected = "release-keys", actual = info.buildTags)
        Assert.assertEquals(info.buildTags, "release-keys")
    }
}

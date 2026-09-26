// PROBLEM: none

// WITH_STDLIB

package sample

import junit.framework.TestCase
import org.testng.Assert

annotation class Serializable

@Serializable
data class Obj(val x: Int)

object Json {
    inline fun <reified T> decodeFromString(@Suppress("UNUSED_PARAMETER") value: String): T = error("stub")
}

class DeviceInfoTest : TestCase() {
    fun testDeviceInfo(info: DeviceInfo, expected: String, actual: String) {
        <caret>assertEquals("release-keys", info.buildTags)
        assertEquals(expected, actual)
        assertEquals(expected = "release-keys", actual = info.buildTags)
        Assert.assertEquals(info.buildTags, "release-keys")
        assertEquals(42u.toUByte(), Json.decodeFromString<UByte>("42"))
        assertEquals(42u.toUShort(), Json.decodeFromString<UShort>("42"))
        assertEquals(Obj(1), Json.decodeFromString<Obj>("{\"x\":1}"))
    }
}

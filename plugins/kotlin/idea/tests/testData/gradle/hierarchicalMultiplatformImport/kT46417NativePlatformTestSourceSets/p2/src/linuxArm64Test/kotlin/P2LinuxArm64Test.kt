import kotlin.test.Test
import kotlin.test.assertEquals

class P2LinuxArm64Test {

    @Test
    fun testCommonMain() {
        val x = P1CommonMain()
        assertEquals(x.invoke(), P1CommonMain())
    }

    @Test
    fun testNativeMain() {
        val x = P1NativeMain()
        assertEquals(x.invoke(), P1NativeMain())
    }

    @Test
    fun testLinuxMain() {
        val x = P1LinuxMain()
        assertEquals(x.invoke(), P1LinuxMain())
    }
}

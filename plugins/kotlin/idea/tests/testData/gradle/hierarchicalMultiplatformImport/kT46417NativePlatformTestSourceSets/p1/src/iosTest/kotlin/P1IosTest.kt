import kotlin.test.Test
import kotlin.test.assertEquals

class P1IosTest {

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
    fun testIosMain() {
        val x = P1IosMain()
        assertEquals(x.invoke(), P1IosMain())
    }

}

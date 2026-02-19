import kotlin.test.Test
import kotlin.test.assertEquals

class <!LINE_MARKER("descr='Run Test'")!>CommonTest<!> {
    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>testAdd<!>() {
        assertEquals(6.0, add(3.5, 2.5))
        assertEquals(0.0, add(-2.0, 2.0))
        assertEquals(-5.0, add(-3.0, -2.0))
    }
}

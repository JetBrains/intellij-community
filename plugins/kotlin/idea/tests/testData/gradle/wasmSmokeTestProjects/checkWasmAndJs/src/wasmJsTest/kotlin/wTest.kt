import kotlin.test.Test
import kotlin.test.assertEquals

class WasmTestClient {
    @Test
    fun testGreet() {
        assertEquals("world", greet())
    }
}

import kotlin.test.Test
import kotlin.test.assertEquals

class JsTestClient {
    @Test
    fun testGreet() {
        assertEquals("world", greet())
    }
}

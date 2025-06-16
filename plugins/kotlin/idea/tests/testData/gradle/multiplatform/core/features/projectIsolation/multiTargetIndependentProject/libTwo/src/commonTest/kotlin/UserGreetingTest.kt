import kotlin.test.Test
import kotlin.test.assertTrue

class <!LINE_MARKER("descr='Run Test'")!>UserGreetingTest<!> {
    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>testPersonalizedGreeting<!>() {
        val result = personalizedGreeting("Alex")
        assertTrue(result.startsWith("Hello, Alex! "))
    }
}

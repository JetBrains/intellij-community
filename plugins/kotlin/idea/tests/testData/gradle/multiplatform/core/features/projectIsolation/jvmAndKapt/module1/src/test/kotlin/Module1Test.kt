import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class <!LINE_MARKER("descr='Run Test'")!>Module1Test<!> {

    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>`test Module1 greet`<!>() {
        val dependency = Dependency()
        val module1 = Module1(dependency)

        val expected = "Hello from Module1 and I'm a dependency!"
        assertEquals(expected, module1.greet())
    }
}

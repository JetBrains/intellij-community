import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class <!LINE_MARKER("descr='Run Test'")!>Module2Test<!> {
    @Test
    fun <!LINE_MARKER("descr='Run Test'")!>`test calculate`<!>() {
        val module2 = Module2()
        assertEquals(5, module2.calculate(2, 3))
    }
}

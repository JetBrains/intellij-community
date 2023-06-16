// CONFIGURE_LIBRARY: JUnit5
package testing

import org.junit.jupiter.api.Test

class <lineMarker descr="Run Test">Simple</lineMarker> {
    @Test
    fun <lineMarker descr="Run Test">foo</lineMarker>() {}
}


class Go {
    class <lineMarker descr="Run Test">Deeper</lineMarker> {
        @Test
        fun <lineMarker descr="Run Test">shouldPass</lineMarker>() {
            assertTrue(true, "pass")
        }
    }
}

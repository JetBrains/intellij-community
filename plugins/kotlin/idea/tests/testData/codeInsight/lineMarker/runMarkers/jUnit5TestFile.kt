// CONFIGURE_LIBRARY: JUnit5
package testing

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import kotlin.test.*

class <lineMarker descr="Run Test">Simple</lineMarker> {
    @Test
    fun <lineMarker descr="Run Test">foo</lineMarker>() {}
}


object <lineMarker descr="Run Test" icon="runConfigurations/testState/run.svg">SessionObjectTest</lineMarker> {
    @Test
    fun <lineMarker descr="Run Test" icon="runConfigurations/testState/run.svg">testSessionCreateDelete</lineMarker>() {}

    @Disabled
    @Test
    fun <lineMarker descr="Run Test" icon="runConfigurations/testState/run.svg">configFileWithEnvironmentVariables</lineMarker>() {}

    @Ignore
    @Test
    fun <lineMarker descr="Run Test" icon="runConfigurations/testState/run.svg">kotlinTestIgnore</lineMarker>() {}

    @Disabled
    fun notAnIgoreTest() {}

    @Test
    fun <lineMarker descr="Run Test" icon="runConfigurations/testState/run.svg">`top level extension function as module function`</lineMarker>() {}
}

class Go {
    class <lineMarker descr="Run Test">Deeper</lineMarker> {
        @Test
        fun <lineMarker descr="Run Test">shouldPass</lineMarker>() {
            assertTrue(true, "pass")
        }
    }
}

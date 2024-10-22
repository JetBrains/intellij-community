// CONFIGURE_LIBRARY: JUnit5
package testing

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.*

class <lineMarker descr="Run Test">Simple</lineMarker> {
    @Test
    private fun testPrivateFunction() {}

    @Test
    fun <lineMarker descr="Run Test">foo</lineMarker>() {}
}


object <lineMarker descr="Run Test" icon="runConfigurations/testState/run.svg">SessionObjectTest</lineMarker> {
    @Test
    fun <lineMarker descr="Run Test" icon="runConfigurations/testState/run.svg">testSessionCreateDelete</lineMarker>() {}

    @Disabled
    @Test
    fun <lineMarker descr="Run Test" icon="runConfigurations/testState/run.svg">configFileWithEnvironmentVariables</lineMarker>() {} // DISABLED_WITH_GRADLE_CONFIGURATION

    @Ignore
    @Test
    fun <lineMarker descr="Run Test" icon="runConfigurations/testState/run.svg">kotlinTestIgnore</lineMarker>() {} // DISABLED_WITH_GRADLE_CONFIGURATION

    @Disabled
    fun notAnIgoreTest() {}

    @Test
    fun <lineMarker descr="Run Test" icon="runConfigurations/testState/run.svg">`top level extension function as module function`</lineMarker>() {}
}

class <lineMarker descr="Run Test">Go</lineMarker> {
    class <lineMarker descr="Run Test">Deeper</lineMarker> {
        @Test
        fun <lineMarker descr="Run Test">shouldPass</lineMarker>() {
            assertTrue(true, "pass")
        }
    }

    @org.junit.jupiter.api.Nested
    inner class <lineMarker descr="Run Test">Inner</lineMarker> {
        fun test1() { }
    }

    class Inner2 {
        fun test1() { }
    }
}

private class <lineMarker descr="Run Test">Simple</lineMarker> {
    @Test
    fun <lineMarker descr="Run Test">foo</lineMarker>() {}
}

class NoTestInside {
    fun foo() {}
}
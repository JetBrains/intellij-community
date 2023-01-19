// CONFIGURE_LIBRARY: TestNG
package testing

import org.testng.annotations.Test

abstract class <lineMarker descr="Is subclassed by KTest (testing) KTest2 (testing) Press ... to navigate"><lineMarker descr="Run Test">KBase</lineMarker></lineMarker> {// LIGHT_CLASS_FALLBACK
    @Test
    fun <lineMarker descr="Run Test">testFoo</lineMarker>() {// LIGHT_CLASS_FALLBACK
    }
}

class <lineMarker descr="Run Test">KTest</lineMarker> : KBase() {// LIGHT_CLASS_FALLBACK
    @Test
    fun <lineMarker descr="Run Test">testBar</lineMarker>() {// LIGHT_CLASS_FALLBACK
    }
}

class <lineMarker descr="Run Test">KTest2</lineMarker> : KBase() {// LIGHT_CLASS_FALLBACK
    @Test
    fun <lineMarker descr="Run Test">testBaz</lineMarker>() {// LIGHT_CLASS_FALLBACK
    }
}
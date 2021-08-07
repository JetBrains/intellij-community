// CONFIGURE_LIBRARY: TestNG
package testing

import org.testng.annotations.Test

abstract class <lineMarker descr="*"><lineMarker descr="Run Test">KBase</lineMarker></lineMarker> {// LIGHT_CLASS_FALLBACK
    @Test
    fun <lineMarker descr="*">testFoo</lineMarker>() {// LIGHT_CLASS_FALLBACK
    }
}

class <lineMarker descr="*">KTest</lineMarker> : KBase() {// LIGHT_CLASS_FALLBACK
    @Test
    fun <lineMarker descr="*">testBar</lineMarker>() {// LIGHT_CLASS_FALLBACK
    }
}

class <lineMarker descr="*">KTest2</lineMarker> : KBase() {// LIGHT_CLASS_FALLBACK
    @Test
    fun <lineMarker descr="*">testBaz</lineMarker>() {// LIGHT_CLASS_FALLBACK
    }
}
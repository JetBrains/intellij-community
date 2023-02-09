// CONFIGURE_LIBRARY: JUnit
package testing

import junit.framework.TestCase
import org.junit.Test

abstract class <lineMarker descr="Is subclassed by KTest (testing) KTest2 (testing) Press ... to navigate"><lineMarker descr="Run Test">KBase</lineMarker></lineMarker> : TestCase() {
    // NOTE: this differs from Java tooling behaviour, see KT-27977
    @Test
    fun <lineMarker descr="Run Test">testFoo</lineMarker>() {

    }
}


class <lineMarker descr="Run Test">KTest</lineMarker> : KBase() {
    @Test
    fun <lineMarker descr="Run Test">testBar</lineMarker>() {

    }
}

class <lineMarker descr="Run Test">KTest2</lineMarker> : KBase() {
    @Test
    fun <lineMarker descr="Run Test">testBaz</lineMarker>() {

    }
}

abstract class <lineMarker descr="Run Test"><lineMarker descr="Run Test">AbstractClassWithoutInheritors</lineMarker></lineMarker> : TestCase() {
    // NOTE: showing line markers for abstract method, which has no inheritors is not ideal, because those methods cannot actually be run
    // Sadly, run configurations can actually be created for them (same in Java), so this behaviour is consistent with context menu
    @Test
    fun <lineMarker descr="Run Test">testFoo</lineMarker>() {

    }
}

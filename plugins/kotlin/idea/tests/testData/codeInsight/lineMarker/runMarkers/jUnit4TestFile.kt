// CONFIGURE_LIBRARY: JUnit4
package testing

import kotlin.test.*
import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.junit.Test

@RunWith(Suite::class)
class <lineMarker descr="Run Test" icon="runConfigurations/testState/run_run.svg">SessionTest0</lineMarker> {// LIGHT_CLASS_FALLBACK
}

class <lineMarker descr="Run Test" icon="runConfigurations/testState/run_run.svg">SessionTest</lineMarker> {
    @Test
    fun <lineMarker descr="Run Test" icon="runConfigurations/testState/run.svg">testSessionCreateDelete</lineMarker>() {

    }

    @Test
    fun <lineMarker descr="Run Test" icon="runConfigurations/testState/run.svg">configFileWithEnvironmentVariables</lineMarker>() {
    }

    @Test
    fun <lineMarker descr="Run Test" icon="runConfigurations/testState/run.svg">`top level extension function as module function`</lineMarker>() {
    }

    class IsolatedRule : TestRule {
        override fun apply(s: Statement, d: Description): Statement {
            return object : Statement() {
                override fun evaluate() {
                    s.evaluate()
                }
            }
        }
    }
}

abstract class <lineMarker descr="Run Test"><lineMarker descr="Is subclassed by FooCase in AbstractFoo (testing) Press ... to navigate">AbstractFoo</lineMarker></lineMarker> {
    inner class FooCase : AbstractFoo() {
        @Test
        fun testFoo() {

        }
    }

    @Test
    fun <lineMarker descr="Run Test">testBaseFoo</lineMarker>() {

    }
}

fun String.foo() {}

class EmptySession
data class TestUserSession(val userId: String, val cart: List<String>)

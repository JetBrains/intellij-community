// CONFIGURE_LIBRARY: JUnit4
package testing

import kotlin.test.*
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
class <lineMarker descr="Run Test">SessionTest0</lineMarker> {

}

class <lineMarker descr="Run Test">SessionTest</lineMarker> {
    @Test
    fun <lineMarker descr="Run Test">testSessionCreateDelete</lineMarker>() {

    }

    @Test
    fun <lineMarker descr="Run Test">configFileWithEnvironmentVariables</lineMarker>() {
    }

    @Test
    fun <lineMarker descr="Run Test">`top level extension function as module function`</lineMarker>() {
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

fun String.foo() {}

class EmptySession
data class TestUserSession(val userId: String, val cart: List<String>)

// CONFIGURE_LIBRARY: JUnit3
package testing

import junit.framework.TestCase
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action

interface <lineMarker descr="*">Some</lineMarker>

open class <lineMarker descr="*">NotATest</lineMarker>: Some {

}

open class NotATest2: NotATest() {

}

class <lineMarker descr="Run Test">SessionTest1</lineMarker>: TestCase() {

}

class <lineMarker descr="Run Test">SessionTest2</lineMarker>: TestCase() {

    override fun <lineMarker descr="Overrides function in 'TestCase'">setUp</lineMarker>() {
        super.setUp()
    }

    override fun <lineMarker descr="Overrides function in 'TestCase'">tearDown</lineMarker>() {
        super.tearDown()
    }
}

class <lineMarker descr="Run Test">SessionTest</lineMarker>: TestCase() {
    fun <lineMarker descr="Run Test">testSessionCreateDelete</lineMarker>() {

    }

    fun configFileWithEnvironmentVariables() {
    }

    fun `top level extension function as module function`() {
    }

    class IsolatedRule {
        fun apply(): Action {
            return object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent?) {
                }
            }
        }
    }

}

fun String.foo() {}

class EmptySession
data class TestUserSession(val userId: String, val cart: List<String>)

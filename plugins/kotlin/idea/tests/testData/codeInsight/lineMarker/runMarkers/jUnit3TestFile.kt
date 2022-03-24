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

class NotATest3: NotATest(){
    fun testMe() {}
}

class NotATest4 {
    fun testMe() {}
}

class <lineMarker descr="Run Test" icon="runConfigurations/testState/run_run.svg">SessionTest1</lineMarker>: TestCase() {

}

class <lineMarker descr="Run Test" icon="runConfigurations/testState/run_run.svg">SessionTest2</lineMarker>: TestCase() {

    override fun <lineMarker descr="Overrides function in 'TestCase'">setUp</lineMarker>() {
        super.setUp()
    }

    override fun <lineMarker descr="Overrides function in 'TestCase'">tearDown</lineMarker>() {
        super.tearDown()
    }
}

class <lineMarker descr="Run Test" icon="runConfigurations/testState/run_run.svg">SessionTest</lineMarker>: TestCase() {
    fun <lineMarker descr="Run Test" icon="runConfigurations/testState/run.svg">testSessionCreateDelete</lineMarker>() {

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

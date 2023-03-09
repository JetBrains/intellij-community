// CONFIGURE_LIBRARY: JUnit3
package testing

import junit.framework.TestCase
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action

interface <lineMarker descr="Is implemented by NotATest (testing) NotATest2 (testing) NotATest3 (testing) Press ... to navigate">Some</lineMarker>

open class <lineMarker descr="Is subclassed by NotATest2 (testing) NotATest3 (testing) Press ... to navigate">NotATest</lineMarker>: Some {

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

    override fun <lineMarker descr="Overrides function in TestCase (junit.framework) Press ... to navigate">setUp</lineMarker>() {
        super.setUp()
    }

    override fun <lineMarker descr="Overrides function in TestCase (junit.framework) Press ... to navigate">tearDown</lineMarker>() {
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

abstract class <lineMarker>AbstractFoo</lineMarker>: TestCase() {
    inner class FooCase : AbstractFoo() {
        fun testFoo() {

        }
    }

    fun <lineMarker descr="Run Test">testBaseFoo</lineMarker>() {

    }
}

class <lineMarker descr="Run Test">AnotherFileTestCaseClassImpl</lineMarker>: AnotherFileTestCaseClass() {// LIGHT_CLASS_FALLBACK
    fun <lineMarker descr="Run Test">testFoo</lineMarker>() {// LIGHT_CLASS_FALLBACK

    }
}

fun String.foo() {}

class EmptySession
data class TestUserSession(val userId: String, val cart: List<String>)

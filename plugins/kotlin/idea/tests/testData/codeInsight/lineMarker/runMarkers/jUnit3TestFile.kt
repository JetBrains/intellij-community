// CONFIGURE_LIBRARY: JUnit3
// LIBRARY_JAR_FILE: someTestLib.jar
package testing

import junit.framework.Test
import junit.framework.TestSuite
import junit.framework.TestCase
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import foo.AbstractSomeTestClass

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

class <lineMarker descr="Run Test">SessionTest1</lineMarker>: TestCase() {

}

class <lineMarker descr="Run Test">SessionTest2</lineMarker>: TestCase() {

    override fun <lineMarker descr="Overrides function in TestCase (junit.framework) Press ... to navigate">setUp</lineMarker>() {
        super.setUp()
    }

    override fun <lineMarker descr="Overrides function in TestCase (junit.framework) Press ... to navigate">tearDown</lineMarker>() {
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

abstract class <lineMarker descr="Is subclassed by FooCase in AbstractFoo (testing) Press ... to navigate"><lineMarker descr="Run Test">AbstractFoo</lineMarker></lineMarker>: TestCase() {
    inner class FooCase : AbstractFoo() {
        fun testFoo() {

        }
    }

    fun <lineMarker descr="Run Test">testBaseFoo</lineMarker>() {

    }
}

object SomeObject: TestCase() {
    fun testButNo() {

    }
}

class <lineMarker descr="Run Test">AnotherFileTestCaseClassImpl</lineMarker>: AnotherFileTestCaseClass() {// LIGHT_CLASS_FALLBACK
    fun <lineMarker descr="Run Test">testFoo</lineMarker>() {// LIGHT_CLASS_FALLBACK

    }
}

fun String.foo() {}

class EmptySession
data class TestUserSession(val userId: String, val cart: List<String>)

// binary class from someTestLib.jar
class <lineMarker descr="Run Test">SomeTestClassImpl</lineMarker>: AbstractSomeTestClass() {

}

object <lineMarker descr="Run Test">TestObjectWithSuite</lineMarker> {
    @JvmStatic
    fun suite(): Test {
        return TestSuite()
    }
}

class <lineMarker descr="Run Test">TestClassWithSuite</lineMarker> {
    companion object {
        @JvmStatic
        fun suite(): Test {
            return TestSuite()
        }
    }
}
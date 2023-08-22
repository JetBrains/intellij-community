import com.intellij.openapi.diagnostic.Logger
import kotlin.jvm.JvmStatic

class MyExtensionImpl : MyExtension {

  fun foobar() {
    val a = g
    quux()
    i = a
  }

  object A { }

  <warning descr="Companion objects in IDE extension implementations may only contain a logger and constants">companion<caret></warning> object {

    // const val are allowed
    const val MY_CONST = 0

    // public and internal members don't cause conflicts
    val a = 0
    //internal fun foo() {
    //  qux(a)
    //}

    fun foo() {
      qux(a)
    }

    // --------------- protected members ---------------
    // protected members are not allowed at top-level but they aren't reported as conflicts since `protected` will be removed
    protected val b = 0

    var c = 0
      protected set(value) { field = value + 1 }

    protected fun bar(): Unit { }

    protected class A

    // --------------- private members ---------------
    // private members can't cause conflicts at top-level regardless to how they are used

    // no usages at all:
    private val d = 0

    var e = 0
      private set(value) { field = value + 1 }

    // usages inside 'declarations to move':
    private val f = 0

    private fun qux(ff: Int = f) {
      c = ff
    }

    // usages outside of companion object:
    private val g = 0

    private fun quux(): Unit { }

    var i = 0
      private set(value) { field = value + 1 }

    // usages in declarations that won't be moved:
    private val toInitLogger: Boolean = false

    // loggers are allowed so won't be moved
    val logger: Logger? = if (toInitLogger) Logger() else null

    // ------------- annotations ------------------------

    @JvmStatic
    fun staticFoo() { }

    @MyAnnotation
    val staticField = 0

  }
}

fun main() {
  // test references
  MyExtensionImpl.foo()
  MyExtensionImpl.a
}

// test name clashing
fun foo() { }
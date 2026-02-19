import com.intellij.openapi.diagnostic.Logger

class MyExtensionImpl : MyExtension {

  fun foobar() {
    val a = g
    quux()
    i = a
  }

  object A { }

  object Util {
    class A
  }
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
    // protected members are not allowed inside a standalone object but not reported as conflicts since the modifier will be removed
    protected val b = 0

    var c = 0
      protected set(value) { field = value + 1 }

    protected fun bar(): Unit { }

    protected class A

    // --------------- private members ---------------
    // private members can cause conflicts if they were used outside the companion object (i.e. inside the class)
    // or in declarations that won't be moved

    // no usages at all (don't cause conflicts):
    private val d = 0

    var e = 0
      private set(value) { field = value + 1 }

    // usages inside 'declarations to move' (don't cause conflicts):
    private val f = 0

    private fun qux(ff: Int = f) {
      c = ff
    }

    // usages outside of companion object (cause conflicts):
    private val g = 0

    private fun quux(): Unit { }

    var i = 0
      private set(value) { field = value + 1 }

    // usages in declarations that won't be moved (cause conflicts):
    private val toInitLogger: Boolean = false

    // loggers are allowed so won't be moved
    val logger: Logger? = if (toInitLogger) Logger() else null

  }
}

fun main() {
  // test references
  MyExtensionImpl.foo()
  MyExtensionImpl.a
}
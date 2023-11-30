import com.intellij.openapi.diagnostic.Logger

class MyExtensionImpl : MyExtension {

  <warning descr="Companion objects in IDE extension implementations may only contain a logger and constants">companion<caret></warning> object {
    // loggers are allowed
    private val LOGGER: Logger = Logger()

    // const val are allowed
    const val MY_CONST = 0

    // test properties' moving
    val s
      get() = ""

    // test functions' moving
    fun foo(): MyClass {
      // test references
      MY_CONST
      s
      return MyClass()
    }

    // test classes' moving
    class MyClass

    // test annotation's moving
    @MyAnnotation
    val a = 0

    // test @JvmStatic
    @JvmStatic
    fun bar() { }

    // test protected memebrs
    protected fun giz() { }

    protected val myVal = 0
  }
}

fun main() {
  // test references
  MyExtensionImpl.foo()
  MyExtensionImpl.a
}
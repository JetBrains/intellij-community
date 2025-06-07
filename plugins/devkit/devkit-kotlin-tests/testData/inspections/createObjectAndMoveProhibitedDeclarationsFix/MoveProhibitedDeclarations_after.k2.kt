import com.intellij.openapi.diagnostic.Logger

class MyExtensionImpl : MyExtension {
    object Util

    object Util1 {
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

        // test protected members moving
        val p = 0
    }

    companion object {
    // loggers are allowed
    private val LOGGER: Logger = Logger()

    // const val are allowed
    const val MY_CONST = 0

    }
}

fun main() {
  // test references
    MyExtensionImpl.Util1.foo()
    MyExtensionImpl.Util1.a
}
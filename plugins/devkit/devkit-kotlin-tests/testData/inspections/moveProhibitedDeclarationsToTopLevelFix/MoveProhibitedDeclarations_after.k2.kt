import com.intellij.openapi.diagnostic.Logger

class MyExtensionImpl : MyExtension {

  companion<caret> object {
    // loggers are allowed
    private val LOGGER: Logger = Logger()

    // const val are allowed
    const val MY_CONST = 0

  }
}

fun main() {
  // test references
    foo()
    a
}

// test properties' moving
val s
  get() = ""

// test functions' moving
fun foo(): MyClass {
  // test references
    MyExtensionImpl.Companion.MY_CONST
  s
  return MyClass()
}

// test classes' moving
class MyClass

// test annotation's moving
@MyAnnotation
val a = 0

// test @JvmStatic
fun bar() { }

// test protected memebrs
fun giz() { }

val myVal = 0
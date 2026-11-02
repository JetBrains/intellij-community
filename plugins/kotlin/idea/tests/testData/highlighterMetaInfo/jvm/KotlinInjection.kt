// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
package language_injection

import org.intellij.lang.annotations.Language

@Language("kotlin")
val test = """
internal class Foo: Bar() {
  private val name: String = "Kodee"
  lateinit var way: Path
  
  fun doFun() {
    var count = 0
    for21@ for (t in pc) {
        while (count < 21) {
            print(message = "Hello, ")
            println(name)

            count++
            // I meant to type `continue@for21`.
            continue@for21
        }
    }
  }
  
  companion object {
     @JvmStatic
     val globalProperty = 42
  }
}
"""

// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
/**
 * - One-liner `fun main(){ println("Hello, Kotlin") }`
 * - No error elements `call(...)`
 * - Kotlin injected and no error elements ``val x = 1`` and ```val name = "Kotlin"```
 * - Markdown triple quotes blocks without a language specification, is a Kotlin code block:
 * ```
 *       // simple comment
 *       fun foo() {
 *         for (i in 0..10) println("i: $i")
 *       }
 * ```
 * - Effectively, it works for multiquoutes blocks, or just indentation blocks
 *
 *    fun bar() {
 *      for (i in 0..10) println("i: $i")
 *    }
 *
 * - For tilde fenced blocks
 * ~~~~
 *     fun foo() {
 *       val x = 1
 *     }
 * ~~~~
 *
 * Some java code
 * ````java
 *     class MyClass {
 *         public static final void main() {
 *         }
 *     }
 * ````
 */
fun f() {}

/**
 * Incomplete blocks
 * ```
 *    fun f() {
 *      println("`asd`")
 *    }
 */
fun fff(){}


/**
 * ~~~
 *    fun f() {
 *
 *    }
 * ~~~
 *
 *    fun f() {
 *      println("`asd`")
 *    }
 *
 */
fun fff2(){}


// "Add dependency on module 'jvm'" "true"
// DISABLE_ERRORS
// FIR_COMPARISON
package bar

import bar.foo.FOO

fun main() {
  val q = <caret>FOO
}
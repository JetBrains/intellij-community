// "Add dependency on module 'jvm'" "true"
// DISABLE-ERRORS
// FIR_COMPARISON
package bar

import bar.foo.fooMethod

fun main() {
  <caret>fooMethod()
}
// "Add dependency on module 'jvm'" "true"
// DISABLE-ERRORS
package bar

import bar.foo.fooMethod

fun main() {
  <caret>fooMethod()
}
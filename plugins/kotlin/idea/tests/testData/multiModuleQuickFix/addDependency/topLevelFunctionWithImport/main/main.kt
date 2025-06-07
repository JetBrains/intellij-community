// "Add dependency on module 'jvm'" "true"
// DISABLE_ERRORS
package bar

fun main() {
  <caret>fooMethod()
}

// IGNORE_K1
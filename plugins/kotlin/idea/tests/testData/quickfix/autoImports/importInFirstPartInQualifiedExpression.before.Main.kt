// "Import class 'Some'" "true"
// ERROR: Unresolved reference: Some

package testing

fun testing() {
  <caret>Some.test()
}
/* IGNORE_FIR */

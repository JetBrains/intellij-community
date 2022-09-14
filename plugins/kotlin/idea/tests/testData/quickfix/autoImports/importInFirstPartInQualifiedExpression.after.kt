// "Import class 'Some'" "true"
// ERROR: Unresolved reference: Some

package testing

import some.Some

fun testing() {
  <caret>Some.test()
}
/* IGNORE_FIR */

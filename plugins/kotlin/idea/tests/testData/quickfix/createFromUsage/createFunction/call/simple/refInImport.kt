// "Create function 'foo'" "false"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: Unresolved reference 'foo'.

package p

import p.<caret>foo

fun test() {

}
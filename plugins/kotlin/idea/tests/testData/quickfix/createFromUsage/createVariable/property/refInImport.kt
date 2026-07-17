// "Create property 'foo'" "false"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: UNRESOLVED_IMPORT
// K2_ERROR: UNRESOLVED_IMPORT

package p

import p.<caret>foo

fun test() {

}
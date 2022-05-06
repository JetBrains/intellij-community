// "Create property 'foo'" "false"
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: foo

package p

import p.<caret>foo

fun test() {

}
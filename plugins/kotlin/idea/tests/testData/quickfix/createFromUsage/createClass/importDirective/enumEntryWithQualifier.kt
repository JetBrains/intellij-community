// "Create enum constant 'A'" "false"
// ERROR: Unresolved reference: A
// K2_AFTER_ERROR: Unresolved reference 'A'.
package p

import p.X.<caret>A

class X {

}
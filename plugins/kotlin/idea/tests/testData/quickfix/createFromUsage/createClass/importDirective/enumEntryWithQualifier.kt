// "Create enum constant 'A'" "false"
// ERROR: Unresolved reference: A
// K2_AFTER_ERROR: UNRESOLVED_IMPORT
// K2_ERROR: UNRESOLVED_IMPORT
package p

import p.X.<caret>A

class X {

}
// "Create class 'A'" "false"
// ERROR: Unresolved reference: A
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
package p

internal fun foo(): Int.<caret>A = throw Throwable("")
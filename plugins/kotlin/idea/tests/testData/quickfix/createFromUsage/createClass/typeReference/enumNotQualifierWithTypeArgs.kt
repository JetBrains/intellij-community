// "Create enum 'A'" "false"
// ERROR: Unresolved reference: A
// K2_AFTER_ERROR: Unresolved reference 'A'.
package p

internal fun foo(): <caret>A<Int, String> = throw Throwable("")
// "Create enum 'A'" "false"
// ACTION: Convert to block body
// ACTION: Create class 'A'
// ACTION: Create interface 'A'
// ACTION: Remove explicit type specification
// ERROR: Unresolved reference: A
package p

internal fun foo(): <caret>A<Int, String> = throw Throwable("")
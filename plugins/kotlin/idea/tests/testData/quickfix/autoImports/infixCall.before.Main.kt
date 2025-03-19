// "Import infix extension function 'H.foo'" "true"
// ERROR: Unresolved reference: foo
// IGNORE_K1
package h

interface H

fun f(h: H) {
    h <caret>foo h
}
// "Import extension function 'H.timesAssign'" "true"
// ERROR: Unresolved reference: *=

package h

interface H

fun f(h: H) {
    h <caret>*= 3
}

/* IGNORE_FIR */
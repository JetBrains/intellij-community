// "Import extension function 'H.timesAssign'" "true"
// ERROR: Unresolved reference: *=

package h

import util.timesAssign

interface H

fun f(h: H) {
    h *= 3
}

/* IGNORE_FIR */
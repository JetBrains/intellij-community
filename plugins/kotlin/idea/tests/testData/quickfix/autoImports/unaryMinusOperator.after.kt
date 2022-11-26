// "Import extension function 'H.unaryMinus'" "true"
// ERROR: Unresolved reference: -

package h

import util.unaryMinus

interface H

fun f(h: H?) {
    -h
}
/* IGNORE_FIR */
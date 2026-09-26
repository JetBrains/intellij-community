// "Import operator 'H.inc'" "true"
// ERROR: Unresolved reference: ++

package h

import util.inc

interface H

fun f(h: H?) {
    var h1 = h
    h1++
}

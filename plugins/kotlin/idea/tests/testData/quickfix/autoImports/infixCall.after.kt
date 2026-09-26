// "Import infix extension function 'H.foo'" "true"
// ERROR: Unresolved reference: foo

package h

import util.foo

interface H

fun f(h: H) {
    h <selection><caret></selection>foo h
}
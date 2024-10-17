// NAME_COUNT_TO_USE_STAR_IMPORT: 3
// IGNORE_K2

import pack1.foo
import pack1.u1
import pack1.u2
import pack1.u3

fun foo(s: String) {
    foo(s) // pack1.foo

    v1 + v2 + v3
    u1 + u2 + u3
}
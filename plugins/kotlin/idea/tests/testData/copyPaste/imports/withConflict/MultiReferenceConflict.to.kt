package b

import a.B
import c.next

<selection>operator fun A.iterator() = B()

fun f() {
    for (i in A()) {
    }
}</selection>
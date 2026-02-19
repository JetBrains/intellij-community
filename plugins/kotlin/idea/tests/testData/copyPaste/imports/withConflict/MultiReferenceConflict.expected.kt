// ERROR: 'next()' is ambiguous for 'iterator()' of type 'B'
package b

import a.A
import a.B
import a.hasNext
import a.next
import c.next

operator fun A.iterator() = B()

fun f() {
    for (i in A()) {
    }
}

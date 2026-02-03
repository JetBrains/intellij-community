package to

import a.A
import a.A.Nested

class B {
    fun foo(): A = A()
}

fun bar(): Nested = Nested()
}

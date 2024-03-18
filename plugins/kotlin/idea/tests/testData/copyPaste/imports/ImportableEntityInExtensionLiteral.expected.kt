package to

import a.A.Nested
import a.ext
import a.f

fun g() {
    f {
        Inner()
        Nested()
        foo()
        ext()
    }
}
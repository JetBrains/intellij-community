package to

import a.ext1
import a.ext2
import b.foo
import b.ext1

fun Int.test() {
    foo()
    ext1()
    a.foo()
    ext1()
    ext2()
}

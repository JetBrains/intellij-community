package to

import a.ext1
import a.ext2

fun Int.ext1() {}

fun Int.test() {
    ext1()
    ext2()
}

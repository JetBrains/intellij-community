// IGNORE_K2
package test

import test.TopLevel.Nested

class TopLevel {
    class Nested
}

fun usage() {
    TopLevel.Nested()
}

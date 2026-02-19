package test

import test.TopLevel.Nested

class TopLevel {
    class Nested
}

fun usage() {
    TopLevel.Nested()
}

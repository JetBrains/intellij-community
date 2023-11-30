package usages

import library.A
fun t() {
    val b = object : A() {
        override fun foo(t: String) {

        }
    }
}
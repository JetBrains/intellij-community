package a

import a.Outer.Inner

object Outer {
    object Inner
}

fun <T> test() = Unit

<selection>
fun main() {
    test<Inner>()
}</selection>

package a

object Outer {
    object Inner
}

fun <T> test() = Unit

<selection>
fun main() {
    test<Outer.Inner>()
}</selection>

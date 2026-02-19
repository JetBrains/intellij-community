package a

class A {
}

fun A.test() {

}

fun <T> test() = Unit

<selection>
fun main() {
    A().test()
    test<A>()
}</selection>
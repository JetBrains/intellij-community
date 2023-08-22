package library
interface X {

}

open class A : X {

}

object O : A() {
    fun foo() {

    }
}

fun A.foo(s: String) {

}

fun X.foo(n: Int) {

}


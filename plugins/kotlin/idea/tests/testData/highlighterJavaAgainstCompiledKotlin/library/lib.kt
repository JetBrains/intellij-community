package lib

class Box<T>

class Lib {
    fun <X> foo(box: Box<X>, x: X) {}
}

class OverloadedLib {
    fun <X> foo(box: Box<X>, x: X) {}
    fun foo(box: Box<String>, x: String) {}
}

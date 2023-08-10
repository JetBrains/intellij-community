// FIX: Convert sealed subclass to object
// WITH_STDLIB

abstract class Base {
    var s: String
        get() = "Hello"
        set(value) {}
}

sealed class Sealed : Base() {
    open val x: List<Int>
        get() = emptyList()
}

<caret>class Derived : Sealed() {
    var length: Int
        get() = s.length
        set(value) {}
}
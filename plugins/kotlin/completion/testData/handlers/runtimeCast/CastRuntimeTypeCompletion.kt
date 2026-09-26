fun main() {
    val holder = Holder()
    <caret>val a = 1
}

class Holder {
    val b: Base = Derived()
}

open class Base

class Derived : Base() {
    fun funInDerived() {}
}

// RUNTIME_TYPE: Derived
// AUTOCOMPLETE_SETTING: true
fun main() {
    val b: Base = Derived()
    <caret>val a = 1
}

open class Base

class Derived : Base() {
    fun process(x: Int) {}
}

// RUNTIME_TYPE: Derived
// AUTOCOMPLETE_SETTING: true

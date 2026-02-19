// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY

class Container {
    val unstableValue: Any?
        get() = null
}

interface Foo

fun Container.testReceiver() {
    if (unstableValue != null) {
        unstableValue
    }

    if (unstableValue is Foo) {
        unstableValue
    }
}

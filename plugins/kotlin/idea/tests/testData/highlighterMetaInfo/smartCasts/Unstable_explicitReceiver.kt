// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY

class Container {
    val unstableValue: Any?
        get() = null
}

interface Foo

fun testParam(container: Container) {
    if (container.unstableValue != null) {
        container.unstableValue
    }

    if (container.unstableValue is Foo) {
        container.unstableValue
    }
}

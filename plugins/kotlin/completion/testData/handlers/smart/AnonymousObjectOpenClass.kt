open class OpenClass {
    open fun greet(): String = "hello"
}

fun test(): OpenClass {
    return <caret>
}

// ELEMENT: object
// IGNORE_K1
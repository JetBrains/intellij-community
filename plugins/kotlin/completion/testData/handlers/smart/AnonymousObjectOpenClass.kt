open class OpenClass {
    open fun greet(): String = "hello"
}

fun test(): OpenClass {
    return <caret>
}

// ELEMENT: object

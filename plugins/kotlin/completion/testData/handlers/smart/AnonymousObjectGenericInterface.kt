interface GenericInterface<T> {
    fun transform(input: T): T
}

fun test(): GenericInterface<String> {
    return <caret>
}

// ELEMENT: object
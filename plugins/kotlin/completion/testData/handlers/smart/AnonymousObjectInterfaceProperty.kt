interface PropertyOnlyInterface {
    val id: Int
    val name: String
}

fun test() : PropertyOnlyInterface {
    return <caret>
}

// ELEMENT: object
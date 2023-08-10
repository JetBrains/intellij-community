enum class E {
    A,
    B
}

fun foo(): E {
    return <caret>
}

// EXIST: { lookupString:"A", itemText:"E.A", tailText:" (<root>)", typeText:"E", icon: "org/jetbrains/kotlin/idea/icons/enumKotlin.svg"}
// EXIST: { lookupString:"B", itemText:"E.B", tailText:" (<root>)", typeText:"E", icon: "org/jetbrains/kotlin/idea/icons/enumKotlin.svg"}

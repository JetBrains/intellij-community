// IGNORE_K1

enum class `KTIJ-25208` {

    `interface`
}

fun bar() {
    i<caret>
}

// EXIST: { itemText: "KTIJ-25208.interface", lookupString: "interface", typeText: "`KTIJ-25208`", icon: "org/jetbrains/kotlin/idea/icons/enumKotlin.svg" }
// INVOCATION_COUNT: 2
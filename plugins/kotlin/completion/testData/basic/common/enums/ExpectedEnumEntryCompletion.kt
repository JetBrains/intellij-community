enum class Enum {

    FOO,
    BAR,
}

fun foo(enum: Enum?) {}

fun bar() {
    foo(<caret>)
}

// EXIST: { itemText: "Enum", lookupString:"Enum", icon: "org/jetbrains/kotlin/idea/icons/enumKotlin.svg" }
// EXIST: { itemText: "Enum.FOO", lookupString: "FOO", typeText: "Enum", icon: "org/jetbrains/kotlin/idea/icons/enumKotlin.svg" }
// EXIST: { itemText: "Enum.BAR", lookupString: "BAR", typeText: "Enum", icon: "org/jetbrains/kotlin/idea/icons/enumKotlin.svg" }
// INVOCATION_COUNT: 1
// IGNORE_K1
// IGNORE_K2
enum class Enum {

    FOO,
    BAR,
}

fun bar() {
    if (<caret> != Enum.FOO) {
    }
}

// EXIST: { itemText: "Enum", lookupString:"Enum", icon: "org/jetbrains/kotlin/idea/icons/enumKotlin.svg" }
// EXIST: { itemText: "Enum.FOO", lookupString: "FOO", typeText: "Enum", icon: "org/jetbrains/kotlin/idea/icons/enumKotlin.svg" }
// EXIST: { itemText: "Enum.BAR", lookupString: "BAR", typeText: "Enum", icon: "org/jetbrains/kotlin/idea/icons/enumKotlin.svg" }
// INVOCATION_COUNT: 1
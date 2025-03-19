// IGNORE_K1

enum class Enum {

    FOO
}

fun bar() {
    FO<caret>
}

// EXIST: { itemText: "Enum.FOO", lookupString:"FOO", typeText: "Enum", icon: "org/jetbrains/kotlin/idea/icons/enumKotlin.svg" }
// INVOCATION_COUNT: 2
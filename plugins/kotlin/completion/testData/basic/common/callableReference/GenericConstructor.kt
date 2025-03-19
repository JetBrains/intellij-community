class Wrapper<T>(val value: T)

fun test() {
    listOf<String>().map(::Wrap<caret>)
}

// IGNORE_K2
// EXIST: { lookupString: "Wrapper", itemText: "Wrapper",    tailText: "(value: T)", attributes: "", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// NOTHING_ELSE
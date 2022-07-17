class Generic1<T>

class Generic2<T1, T2>

fun foo(): G<caret>

// EXIST: { lookupString: "Generic1", itemText: "Generic1", tailText: "<T> (<root>)", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// EXIST: { lookupString: "Generic2", itemText: "Generic2", tailText: "<T1, T2> (<root>)", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}

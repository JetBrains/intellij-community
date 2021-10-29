package test

typealias MySameFileAlias = (String) -> Int
private typealias MyPrivateAlias = (String, Char) -> Unit

val test: My<caret>

// EXIST: { lookupString: "MySameFileAlias", itemText: "MySameFileAlias", tailText: " (test)", typeText: "(String) -> Int", attributes: "", icon: "org/jetbrains/kotlin/idea/icons/typeAlias.svg"}
// EXIST: { lookupString: "MyPrivateAlias", itemText: "MyPrivateAlias", tailText: " (test)", typeText: "(String, Char) -> Unit", attributes: "", icon: "org/jetbrains/kotlin/idea/icons/typeAlias.svg"}
// EXIST: { lookupString: "MyAlias", itemText: "MyAlias", tailText: " (dependency)", typeText: "MyClass", attributes: "", icon: "org/jetbrains/kotlin/idea/icons/typeAlias.svg"}

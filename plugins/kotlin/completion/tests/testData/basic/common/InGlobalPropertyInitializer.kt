val testing = 12
val test = "Hello"

val more = test<caret>

// EXIST: { lookupString: "test", itemText: "test", tailText: " (<root>)", typeText: "String", attributes: "", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}
// EXIST: { lookupString: "testing", itemText: "testing", tailText: " (<root>)", typeText: "Int", attributes: "", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}

fun f(nested: Outer.Nested?){}
fun f(nested: Outer.Nested.NestedNested?){}

fun foo(nest<caret>)

// IGNORE_K2
// EXIST: { lookupString: "nested: Nested", itemText: "nested: Outer.Nested?", tailText: " (<root>)", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// EXIST: { lookupString: "nested: Nested", itemText: "nested: Outer.Nested", tailText: " (<root>)", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// EXIST: { lookupString: "nested: NestedNested", itemText: "nested: Outer.Nested.NestedNested?", tailText: " (<root>)", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// EXIST: { lookupString: "nested: NestedNested", itemText: "nested: Outer.Nested.NestedNested", tailText: " (<root>)", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// EXIST: { lookupString: "nested: Nested", itemText: "nested: JavaOuter.Nested", tailText: " (<root>)", icon: "RowIcon(icons=[Class, null])"}
// EXIST: { lookupString: "nested: NestedNested", itemText: "nested: JavaOuter.Nested.NestedNested", tailText: " (<root>)", icon: "RowIcon(icons=[Class, null])"}

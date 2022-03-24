package pack

class FooBar

class Boo

fun f(myB<caret>)

// EXIST: { lookupString: "myBar: FooBar", itemText: "myBar: FooBar", tailText: " (pack)", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// ABSENT: { itemText: "myBFooBar: FooBar" }
// EXIST: { lookupString: "myBoo: Boo", itemText: "myBoo: Boo", tailText: " (pack)", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}

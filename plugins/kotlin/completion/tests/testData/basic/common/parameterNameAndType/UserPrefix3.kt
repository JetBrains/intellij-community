package pack

class FooFaa

class Fuu

fun f(myFooF<caret>)

// EXIST: { lookupString: "myFooFaa: FooFaa", itemText: "myFooFaa: FooFaa", tailText: " (pack)", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// EXIST: { lookupString: "myFooFuu: Fuu", itemText: "myFooFuu: Fuu", tailText: " (pack)", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// ABSENT: { itemText: "myFooFooFaa: FooFaa" }

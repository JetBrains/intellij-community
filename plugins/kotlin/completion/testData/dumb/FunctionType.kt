fun functionWithType(a: Int, b: SomeType): ReturnType {

}

fun unitFunction() {

}

val a = <caret>

// EXIST: { lookupString:"functionWithType", tailText:"(a: Int, b: SomeType)", typeText: "ReturnType" }
// EXIST: { lookupString:"unitFunction", tailText:"()" }
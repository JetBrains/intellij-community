// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments


context(ctx: String)
fun foo(i: Int) {}

fun test() {
    foo(ctx = "hello", <caret>)
}

// ABSENT: { itemText: "ctx =" }
// EXIST: { itemText: "i =", tailText: " Int" }

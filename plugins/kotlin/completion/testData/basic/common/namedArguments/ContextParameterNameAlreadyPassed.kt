// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// IGNORE_K1

context(ctx: String)
fun foo(i: Int) {}

fun test() {
    foo(ctx = "hello", <caret>)
}

// ABSENT: { itemText: "ctx =" }
// EXIST: { itemText: "i =", tailText: " Int" }

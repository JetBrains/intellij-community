// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments


context(ctx: String)
fun foo(i: Int) {}

fun test() {
    foo(i = 1, <caret>)
}

// EXIST: { itemText: "ctx =", tailText: " String" }

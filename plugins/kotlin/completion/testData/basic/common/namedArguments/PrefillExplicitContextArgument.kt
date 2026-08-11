// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments


context(ctx: String, other: Int)
fun foo(i: Int) {}

fun test(ctx: String, other: Int) {
    foo(i = 1, <caret>)
}

// EXIST: { itemText: "ctx = ctx" }
// ABSENT: { itemText: "other = other" }

// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments


context(ctx: String)
fun foo(i: Int) {}

fun test(i: Int, ctx: String, other: Int) {
    foo(<caret>)
}

// EXIST: { itemText: "i = i" }
// ABSENT: { itemText: "ctx = ctx" }
// ABSENT: { itemText: "other = other" }

// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// IGNORE_K1

context(sooLong: String)
fun foo(i: Int) {}

fun test() {
    foo(s<caret>)
}

// EXIST: { itemText: "sooLong =", tailText: " String" }

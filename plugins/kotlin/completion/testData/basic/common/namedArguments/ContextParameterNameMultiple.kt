// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments


context(ctx2: String, ctx1: Int)
fun foo(i: Long) {}

fun test() {
    foo(
        <caret>
    )
}

// WITH_ORDER
// EXIST: { itemText: "i =", tailText: " Long" }
// EXIST: { itemText: "ctx2 =", tailText: " String" }
// EXIST: { itemText: "ctx1 =", tailText: " Int" }

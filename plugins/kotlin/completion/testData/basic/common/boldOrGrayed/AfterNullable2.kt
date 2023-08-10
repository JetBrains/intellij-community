// FIR_COMPARISON
// FIR_IDENTICAL
fun <T: Any> T.foo() {}

fun test(a: Any?)  {
    a.f<caret>
}

// EXIST: { lookupString: "foo", attributes: "grayed", icon: "Function"}
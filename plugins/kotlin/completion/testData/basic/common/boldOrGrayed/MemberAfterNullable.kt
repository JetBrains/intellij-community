class A {
    fun foo() {}
}

fun test(a: A?) {
    a?.fo<caret>
}

// EXIST: { lookupString: "foo", attributes: "bold", icon: "Method"}
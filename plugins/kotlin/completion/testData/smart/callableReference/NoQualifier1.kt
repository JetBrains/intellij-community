fun foo(p: () -> Unit){}

fun bar() {
    foo(<caret>)
}

fun f1(){}
fun f2(i: Int){}

// EXIST: { lookupString: "::f1", itemText: "::f1", tailText: "() (<root>)", typeText: "Unit" }
// ABSENT: ::f2
// ABSENT: ::Unit
// ABSENT: ::Nothing
// ABSENT: { lookupString:"object" }

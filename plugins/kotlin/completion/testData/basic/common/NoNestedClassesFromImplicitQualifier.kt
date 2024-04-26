class Outer {
    class Nested
    inner class Inner
}

fun Outer.foo() {
    <caret>
}

// IGNORE_K2
// ABSENT: Nested
// EXIST: { itemText: "Inner", tailText: "()", typeText: "Outer.Inner" }

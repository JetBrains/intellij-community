// FIR_COMPARISON
// FIR_IDENTICAL
class C {
    class Nested
    inner class Inner1
    inner class Inner2(s: String)
}

fun foo(c: C) {
    c.<caret>
}

// ABSENT: Nested
// EXIST: { lookupString: "Inner1", itemText: "Inner1", tailText: "()", typeText: "C.Inner1", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// EXIST: { lookupString: "Inner2", itemText: "Inner2", tailText: "(s: String)", typeText: "C.Inner2", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}

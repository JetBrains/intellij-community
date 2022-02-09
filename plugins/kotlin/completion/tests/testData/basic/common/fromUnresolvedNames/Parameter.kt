// FIR_IDENTICAL
// FIR_COMPARISON
// RUN_HIGHLIGHTING_BEFORE

fun foo(p: Int, <caret>) {
    print(unresolvedInFoo1)
    if (p > 0) {
        print(unresolvedInFoo2)
    }
}

fun bar() {
    print(unresolvedInBar)
}

// EXIST: { lookupString: "unresolvedInFoo1", itemText: "unresolvedInFoo1", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// EXIST: { lookupString: "unresolvedInFoo2", itemText: "unresolvedInFoo2", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// ABSENT: unresolvedInBar

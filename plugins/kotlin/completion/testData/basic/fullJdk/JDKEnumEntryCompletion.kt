// FIR_IDENTICAL
// FIR_COMPARISON
fun foo() {
    FO<caret>
}

// { itemText: "FileVisitOption.FOLLOW_LINKS", lookupString: "FOLLOW_LINKS", tailText: "", typeText: "FileVisitOption", icon="" } TODO
// EXIST: { itemText: "PathWalkOption.FOLLOW_LINKS", lookupString: "FOLLOW_LINKS", typeText: "PathWalkOption", icon="org/jetbrains/kotlin/idea/icons/enumKotlin.svg" }
// INVOCATION_COUNT: 2
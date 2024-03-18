fun foo(javaClass: JavaClass) {
    javaClass.<caret>
}

// EXIST: { lookupString: "something", attributes: "bold", icon: "org/jetbrains/kotlin/idea/icons/field_variable.svg"}
// EXIST: { lookupString: "setSomething", attributes: "bold", icon: "nodes/abstractMethod.svg"}
// ABSENT: getSomething
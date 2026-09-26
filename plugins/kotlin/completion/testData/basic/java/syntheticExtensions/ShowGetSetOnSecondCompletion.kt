fun foo(thread: Thread) {
    thread.<caret>
}

// IGNORE_K2
// INVOCATION_COUNT: 2
// EXIST: { lookupString: "priority", itemText: "priority", tailText: " (from getPriority()/setPriority())", typeText: "Int", icon: "org/jetbrains/kotlin/idea/icons/field_variable.svg"}
// EXIST: getPriority
// EXIST: setPriority

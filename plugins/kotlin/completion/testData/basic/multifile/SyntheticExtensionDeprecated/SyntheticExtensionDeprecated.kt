fun foo(javaClass: JavaClass) {
    javaClass.<caret>
}

// WITH_ORDER
// EXIST: { lookupString: "something3", attributes: "bold", icon: "org/jetbrains/kotlin/idea/icons/field_variable.svg"}
// EXIST: { lookupString: "something2", attributes: "bold", icon: "org/jetbrains/kotlin/idea/icons/field_variable.svg"}
// EXIST: { lookupString: "something1", attributes: "bold strikeout", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}
// EXIST: { lookupString: "something4", attributes: "bold strikeout", icon: "org/jetbrains/kotlin/idea/icons/field_variable.svg"}

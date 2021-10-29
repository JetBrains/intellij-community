fun some(a: Double<caret>) {
}

// INVOCATION_COUNT: 2
// WITH_ORDER
// EXIST: { lookupString:"Double", tailText:" (kotlin)", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// EXIST_JAVA_ONLY: { lookupString:"Double", tailText:" (java.lang)" }
// EXIST: { lookupString:"DoubleArray", tailText:" (kotlin)", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}

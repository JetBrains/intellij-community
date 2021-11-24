class Test {
    val St<caret>
}

// INVOCATION_COUNT: 1
// EXIST: { lookupString:"String", tailText:" (kotlin)", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// EXIST: IllegalStateException
// EXIST: StringBuilder
// EXIST_JAVA_ONLY: StringBuffer
// ABSENT: HTMLStyleElement

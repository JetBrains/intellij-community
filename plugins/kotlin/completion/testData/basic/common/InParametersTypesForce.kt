// FIR_COMPARISON
// FIR_IDENTICAL
class SomeClass {
  class SomeInternal

  fun some(a : S<caret>)
}

// INVOCATION_COUNT: 2
// EXIST: SomeClass
// EXIST: SomeInternal
// EXIST: { lookupString:"String", tailText:" (kotlin)", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// EXIST: IllegalStateException
// EXIST: StringBuilder
// EXIST_JAVA_ONLY: StringBuffer
// EXIST_JS_ONLY: HTMLStyleElement
// EXIST_JAVA_ONLY: { lookupString:"Statement", tailText:" (java.sql)" }
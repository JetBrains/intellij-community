// PLATFORM: Common
// FILE: Foo.kt
// MAIN
@Ta<caret>rget(AnnotationTarget.CLASS)
annotation class FooBar

// PLATFORM: Jvm
// FILE: Foo.kt
@Target(AnnotationTarget.CLASS)
annotation class Foo

// PLATFORM: Js
// FILE: Foo.kt
annotation class Bar
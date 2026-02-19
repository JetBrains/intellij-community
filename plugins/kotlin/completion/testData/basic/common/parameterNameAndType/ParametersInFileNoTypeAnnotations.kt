// FIR_COMPARISON
// FIR_IDENTICAL
@Target(AnnotationTarget.TYPE)
annotation class Foo

fun foo(xxxv: @Foo Int) {}

fun test(xxx<caret>)

// EXIST: { lookupString: "xxxv: Int", itemText: "xxxv: Int", tailText: " (kotlin)", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// NOTHING_ELSE
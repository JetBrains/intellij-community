// "Opt-in for 'A::class' on containing file 'appendFileAnnotationToOtherFileAnnotations.kt'" "true"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_RUNTIME
@file:JvmName("Foo")

package p

@RequiresOptIn
annotation class A

@A
fun f() {}

fun g() {
    <caret>f()
}

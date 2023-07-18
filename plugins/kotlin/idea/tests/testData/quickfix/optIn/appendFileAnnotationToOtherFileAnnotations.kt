// "Opt in for 'A' in containing file 'appendFileAnnotationToOtherFileAnnotations.kt'" "true"
// WITH_STDLIB

@file:JvmName("Foo")

package p

@RequiresOptIn
annotation class A

@A
fun f() {}

fun g() {
    <caret>f()
}

// "Opt in for 'B' in containing file 'existingFileAnnotationWithPackage.kt'" "true"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_RUNTIME
@file:OptIn(A::class)

package p

@RequiresOptIn
annotation class A

@RequiresOptIn
annotation class B

@A
fun f() {}

@B
fun g() {}

fun h() {
    <caret>g()
}

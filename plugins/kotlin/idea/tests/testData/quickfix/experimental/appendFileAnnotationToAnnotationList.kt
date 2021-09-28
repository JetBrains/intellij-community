// "Add '@OptIn(A::class)' annotation to containing file 'appendFileAnnotationToAnnotationList.kt'" "true"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_RUNTIME
@file:[
    JvmName("Foo")
    OptIn(B::class)
    Suppress("UNSUPPORTED_FEATURE")
]

package p

@RequiresOptIn
annotation class A

@RequiresOptIn
annotation class B

@A
fun f() {}

fun g() {
    <caret>f()
}

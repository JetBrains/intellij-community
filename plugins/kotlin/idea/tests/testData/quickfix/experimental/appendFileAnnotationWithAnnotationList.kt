// "Opt-in for 'A::class' on containing file 'appendFileAnnotationWithAnnotationList.kt'" "true"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_STDLIB
@file:[
    JvmName("Foo")
]

package p

@RequiresOptIn
annotation class A

@A
fun f() {}

fun g() {
    <caret>f()
}

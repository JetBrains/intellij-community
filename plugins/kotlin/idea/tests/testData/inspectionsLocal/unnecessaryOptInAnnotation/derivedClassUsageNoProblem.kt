// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// PROBLEM: none

@OptIn(B<caret>1::class)
fun a1() {
    val p = b1()
}

@RequiresOptIn("", level = RequiresOptIn.Level.ERROR)
annotation class B1


@B1
class B {
    class A
}

@OptIn(B1::class)
fun b1(): B.A {
    TODO("Not yet implemented")
}

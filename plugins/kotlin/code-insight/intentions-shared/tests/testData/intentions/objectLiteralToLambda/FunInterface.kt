// AFTER-WARNING: Variable 'aaa' is never used

fun interface A {
    fun m()
}

fun foo() {
    val aaa: A = obje<caret>ct : A {
        override fun m() {
        }
    }
}

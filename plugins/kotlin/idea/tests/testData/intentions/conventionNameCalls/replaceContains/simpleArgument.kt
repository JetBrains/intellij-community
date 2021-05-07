// IGNORE_FE10_BINDING_BY_FIR
// AFTER-WARNING: Parameter 'a' is never used
fun test() {
    class Test{
        operator fun contains(a: Int) : Boolean = true
    }
    val test = Test()
    test.c<caret>ontains(1)
}

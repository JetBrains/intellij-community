// WITH_STDLIB
// AFTER-WARNING: Parameter 'f' is never used
// AFTER-WARNING: Variable 'x' is never used
// AFTER-WARNING: Variable 'z' is never used
data class A(var x: Int)

fun convert(f: (A) -> Unit) {}

fun test() {
    convert <caret>{
        val x = it.x

        run {
            val x = 1
            val z = it.x
        }
    }
}
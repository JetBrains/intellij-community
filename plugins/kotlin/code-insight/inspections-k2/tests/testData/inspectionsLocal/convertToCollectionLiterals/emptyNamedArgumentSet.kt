// COMPILER_ARGUMENTS: -Xcollection-literals
class A(val params: Array<String>)

fun test() {
    val a = A(params = arrayOf<caret>())
}

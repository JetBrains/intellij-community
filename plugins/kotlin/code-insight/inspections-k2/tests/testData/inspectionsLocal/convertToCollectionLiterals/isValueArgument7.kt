// COMPILER_ARGUMENTS: -Xcollection-literals
fun withCallback(action: () -> Unit) {}
fun process(items: List<Int>) {}

fun main() {
    withCallback { process(listOf<caret>(1, 2, 3)) }
}

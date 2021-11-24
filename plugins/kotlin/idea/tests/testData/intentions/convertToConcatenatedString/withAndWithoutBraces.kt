// WITH_STDLIB
// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'z' is never used
fun main(args: Array<String>){
    val y = "cde"
    val z = "<caret>${listOf(1, 2)}$y.bar"
}

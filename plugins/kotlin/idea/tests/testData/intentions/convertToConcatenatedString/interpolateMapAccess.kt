// WITH_STDLIB
// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'y' is never used
fun main(args: Array<String>){
    val x = mapOf("a" to "b")
    val y = "<caret>abcd${x["a"]}"
}

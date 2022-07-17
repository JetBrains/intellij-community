// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'x' is never used
fun main(args: Array<String>){
    val a = "abc"
    val c = "bcd"
    val x = a +<caret> "b" + c
}

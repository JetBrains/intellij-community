// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'x' is never used
fun main(args: Array<String>){
    val x = "abc" +<caret> 1 + 2 + 'a' + 3.2
}

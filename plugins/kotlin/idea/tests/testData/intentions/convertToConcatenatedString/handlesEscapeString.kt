// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'x' is never used
fun main(args: Array<String>){
    var t = "t"
    val x = "<caret>abc\n${t}bar\tfoo"
}

// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'a' is never used
fun main(args: Array<String>){
    val a = "<caret>${when (1) {
        1 -> 42
        else -> 3
    }}asdfas${when (1) {
        1 -> 42
        else -> 3
    }}"
}

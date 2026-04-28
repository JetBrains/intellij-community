// IS_APPLICABLE: false
// ERROR: Unresolved reference: /
// K2_ERROR: Unresolved reference 'div' for operator '/' on receiver of type 'String'.
fun main(args: Array<String>){
    val x = "def" /<caret> "abc"
}

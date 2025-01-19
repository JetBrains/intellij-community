// IS_APPLICABLE: false
// ERROR: Unresolved reference: /
// K2-ERROR: Unresolved reference 'div' for operator '/'.
fun main(args: Array<String>){
    val x = "def" /<caret> "abc"
}

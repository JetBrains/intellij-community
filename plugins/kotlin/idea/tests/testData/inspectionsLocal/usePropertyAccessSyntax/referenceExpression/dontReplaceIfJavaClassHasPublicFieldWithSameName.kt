// PROBLEM: none
fun main(){
    val t = TestClass().apply {
        value = 1
        <caret>setValue(1)
    }
}
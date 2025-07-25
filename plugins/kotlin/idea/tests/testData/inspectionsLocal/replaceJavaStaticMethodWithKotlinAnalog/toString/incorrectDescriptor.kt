// RUNTIME_WITH_FULL_JDK
// PROBLEM: none
// K2_ERROR: None of the following candidates is applicable:<br><br>static fun toString(i: Int, radix: Int): String!:<br>  No value passed for parameter 'i'.<br>  No value passed for parameter 'radix'.<br><br>static fun toString(i: Int): String!:<br>  No value passed for parameter 'i'.<br><br>
// ERROR: None of the following functions can be called with the arguments supplied: <br>public open fun toString(i: Int): String! defined in java.lang.Integer<br>public open fun toString(i: Int, radix: Int): String! defined in java.lang.Integer

fun main() {
    println(Integer.toString(<caret>))
}

// RUNTIME_WITH_FULL_JDK
// PROBLEM: none
// ERROR: None of the following functions can be called with the arguments supplied: <br>public open fun toString(i: Int): String! defined in java.lang.Integer<br>public open fun toString(i: Int, radix: Int): String! defined in java.lang.Integer
// K2_ERROR: NONE_APPLICABLE
// K2_ERROR: NO_VALUE_FOR_PARAMETER

fun main() {
    println(Integer.toString(<caret>))
}

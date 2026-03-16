// PROBLEM: none
class Message {
    fun isNotBlank(): Boolean = true
}

fun test4(msg: Message?) {
    if (<caret>msg != null && msg.isNotBlank()) {}
}
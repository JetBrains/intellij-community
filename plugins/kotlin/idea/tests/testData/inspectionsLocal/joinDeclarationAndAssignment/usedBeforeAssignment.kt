// PROBLEM: none
fun test() {
    <caret>lateinit var info: String
    addActionListener {
        println(info)
    }
    info = ""
}

fun addActionListener(callback: () -> Unit) {
}

fun println(a: Any) {
}


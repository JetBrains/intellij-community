// WITH_STDLIB
fun test() {
    when<caret> (val a = create()) {
        else -> use("")
    }
}

fun create(): String = ""

fun use(s: String) {}
// WITH_STDLIB
fun test() {
    when<caret> (val a = create()) {
        else -> {
            use("")
            foo()
        }
    }
}

fun create(): String = ""

fun use(s: String) {}

fun foo() {}
// WITH_STDLIB
fun test() {
    when<caret> (val a = create()) {
        else -> {
            use(a, a)
            foo()
        }
    }
}

fun create(): String = ""

fun use(s: String, t: String) {}

fun foo() {}
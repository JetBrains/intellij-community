// WITH_STDLIB
fun test() {
    when<caret> (val a = 42) {
        else -> {
            use("")
            foo()
        }
    }
}

fun use(s: String) {}

fun foo() {}
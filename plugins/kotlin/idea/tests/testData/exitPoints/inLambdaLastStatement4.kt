fun foo(f: (String?) -> Int) {}

fun test() {
    foo {
        if (it != null) return@foo 1
        if (it == "a") {
            val q = 1~
            2
        } else {
            0
        }
    }
}
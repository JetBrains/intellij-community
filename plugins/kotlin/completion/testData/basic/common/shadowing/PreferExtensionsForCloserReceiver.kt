// FIR_COMPARISON
// FIR_IDENTICAL

fun Int.xxx() {}
fun String.xxx() {}

fun test(n: Int, s: String) {
    with (s) {
        with (n) {
            xx<caret>
        }
    }
}

// EXIST: {"lookupString":"xxx","tailText":"() for Int in <root>","typeText":"Unit","icon":"Function"}
// NOTHING_ELSE

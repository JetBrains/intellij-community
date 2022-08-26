// "Import extension function 'BBB.aaa'" "true"
// WITH_STDLIB
// ERROR: Unresolved reference: aaa

fun test() {
    AAA().apply {
        sub {
            aaa<caret>()
        }
    }
}
/* IGNORE_FIR */
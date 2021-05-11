import BExtSpace.aaa

// "Import" "true"
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
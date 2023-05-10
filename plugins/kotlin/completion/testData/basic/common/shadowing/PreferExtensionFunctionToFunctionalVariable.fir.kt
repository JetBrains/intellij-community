// FIR_COMPARISON
fun Int.xxx() {}
val xxx: Int.() -> Unit
    get() = {}

fun Int.test() = this.xx<caret>

// EXIST: {"lookupString":"xxx","tailText":"() for Int in <root>","typeText":"Unit","icon":"Function"}
// NOTHING_ELSE
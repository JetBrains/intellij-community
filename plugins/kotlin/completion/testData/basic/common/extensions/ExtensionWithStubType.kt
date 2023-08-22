// FIR_COMPARISON
// FIR_IDENTICAL
infix fun <T> Boolean.extension(other: T): T? = if (this) other else null

fun test(b: Boolean) {
    b.exten<caret>
}
// EXIST: {"lookupString":"extension","tailText":"(other: T) for Boolean in <root>","typeText":"T?","icon":"Function"}
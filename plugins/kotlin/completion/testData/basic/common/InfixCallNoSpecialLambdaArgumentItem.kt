// FIR_IDENTICAL
// FIR_COMPARISON
// checks that no special item "ext1 { String, Int -> ... }" created for infix call
infix fun Int.ext1(handler: (String, Int) -> Unit){}
infix fun Int.ext2(c: Char){}

fun foo() {
    val pair = 1 ext<caret>
}

// EXIST: {"lookupString":"ext1","tailText":"(handler: (String, Int) -> Unit) for Int in <root>","typeText":"Unit","icon":"Function","attributes":"bold"}
// EXIST: {"lookupString":"ext2","tailText":"(c: Char) for Int in <root>","typeText":"Unit","icon":"Function","attributes":"bold"}
// NOTHING_ELSE

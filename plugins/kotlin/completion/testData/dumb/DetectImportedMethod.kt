val a = 3.to<caret>

fun test() {
    5.toInt()
}


// EXIST: { lookupString:"toInt", tailText:"()" }
// NOTHING_ELSE
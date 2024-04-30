fun Int.toInt() {

}

val a = 5.to<caret>

// EXIST: { lookupString:"toInt", tailText:"()" }
// NOTHING_ELSE
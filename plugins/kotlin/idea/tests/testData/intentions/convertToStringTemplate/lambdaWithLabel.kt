// AFTER-WARNING: Variable 's' is never used
fun test() {
    val s = <caret>"a" + x@{ return@x }
}

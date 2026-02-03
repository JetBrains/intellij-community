// "Create parameter 'foo'" "false"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: Unresolved reference 'foo'.

fun refer() {
    val v = f<caret>oo<String>("test")
}

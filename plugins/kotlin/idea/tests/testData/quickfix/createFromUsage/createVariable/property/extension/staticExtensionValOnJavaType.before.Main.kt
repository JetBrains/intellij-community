// "Create extension property 'foo'" "false"
// ERROR: Unresolved reference: foo

fun test() {
    val a: Int = J.<caret>foo
}

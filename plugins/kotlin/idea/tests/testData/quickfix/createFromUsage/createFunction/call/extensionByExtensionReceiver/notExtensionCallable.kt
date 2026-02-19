// "Create extension function 'foo'" "false"
// ERROR: Unresolved reference: foo
fun bar(b: Boolean) {

}

fun test() {
    bar(<caret>foo(1))
}
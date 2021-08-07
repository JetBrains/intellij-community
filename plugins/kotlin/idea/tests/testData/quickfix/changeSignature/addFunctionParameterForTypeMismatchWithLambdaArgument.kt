// "Add 1st parameter to function 'foo'" "true"
fun foo(f: () -> Unit) {}

fun test() {
    foo(""<caret>) {}
}
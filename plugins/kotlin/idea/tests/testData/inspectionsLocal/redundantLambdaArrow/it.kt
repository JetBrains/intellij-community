// PROBLEM: "Redundant lambda arrow"
fun foo(f: (String) -> Unit) {}

fun print(s: String) {}

fun bar() {
    foo { <caret>it ->
        print(it)
        print(it)
    }
}

fun foo(a: Int, b: String, c: String) {}
fun foo(a: Int, b: String) {}

fun bar(b: String, a: Int, c: String) {
    foo(<caret>)
}

// EXIST: "a, b, c"
// EXIST: "a, b"

// IGNORE_K2
// todo for K2: Smart completion in K2 is not invoked here because there is no single expected type because
//  there are two signatures that match

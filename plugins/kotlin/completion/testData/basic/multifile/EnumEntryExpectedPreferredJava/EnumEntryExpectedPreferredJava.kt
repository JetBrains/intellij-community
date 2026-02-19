import a.JavaEnum

class OtherClass
fun bar(a: JavaEnum) {
}

fun test() {
    bar(<caret>)
}

// WITH_ORDER
// EXIST: FOO
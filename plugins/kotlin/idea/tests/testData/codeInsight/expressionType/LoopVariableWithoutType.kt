package test

class Foo

fun test(list: List<Foo>) {
    for (<caret>foo in list) {}
}

// TYPE: foo -> <html>Foo</html>

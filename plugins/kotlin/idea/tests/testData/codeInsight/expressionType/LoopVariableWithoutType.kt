package test

class Foo

fun test(list: List<Foo>) {
    for (<caret>foo in list) {}
}

// K1_TYPE: foo -> <html>Foo</html>

// K2_TYPE: foo -> <b>Foo</b>

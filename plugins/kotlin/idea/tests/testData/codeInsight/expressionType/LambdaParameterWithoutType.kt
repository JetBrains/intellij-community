package test

class Foo

fun test(list: List<Foo>) {
    list.forEach { <caret>foo -> }
}

// K1_TYPE: foo -> <html>Foo</html>

// K2_TYPE: foo -> <b>Foo</b>

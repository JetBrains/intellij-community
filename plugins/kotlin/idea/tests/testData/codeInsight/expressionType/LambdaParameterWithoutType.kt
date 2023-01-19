package test

class Foo

fun test(list: List<Foo>) {
    list.forEach { <caret>foo -> }
}

// TYPE: foo -> <html>Foo</html>

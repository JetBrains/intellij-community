package test

class Foo

fun test(list: List<Foo>) {
    list.forEach { <caret>foo: Foo -> }
}

// TYPE: { foo: Foo -> } -> <html>(Foo) -&gt; Unit</html>
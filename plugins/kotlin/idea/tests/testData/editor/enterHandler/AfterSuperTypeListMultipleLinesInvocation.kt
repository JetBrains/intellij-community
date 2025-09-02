class Foo<Int>(someParam: Int, otherParam: () -> Unit) : Bar<Int>(),
            Baz,
            Other<Int>(object: Test { class Inner }) {<caret>
}

class Foo<T>
fun <K>  Foo<K>.foo(i: Int, s: String) {}
fun <K>  Foo<K>.foo(s: String) {}
fun m(f: Foo<String>) {
    f.foo(<caret>)
}

/*
Text: (<highlight>i: Int</highlight>, s: String), Disabled: false, Strikeout: false, Green: false
Text: (<highlight>s: String</highlight>), Disabled: false, Strikeout: false, Green: false
*/

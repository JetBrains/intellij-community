// IGNORE_K1
// IGNORE_K2
// LANGUAGE_VERSION: 2.1

interface Foo {

    fun getFoo(): Foo = this

    fun setFoo(foo: Foo) {}
}

fun test() {
    Bar().<caret>
}

// EXIST: { "itemText": "getFoo", "tailText": "()", "typeText": "Foo", "lookupString": "foo", "allLookupStrings": "foo, getFoo" }
// EXIST: { "itemText": "setFoo", "tailText": "(foo: Foo)", "typeText": "Foo", "lookupString": "foo", "allLookupStrings": "foo, setFoo" }
// EXIST: { "itemText": "bar", "tailText": " (from getBar()/setBar())", "typeText": "Bar!", "lookupString": "bar", "allLookupStrings": "foo, getBar, setBar" }
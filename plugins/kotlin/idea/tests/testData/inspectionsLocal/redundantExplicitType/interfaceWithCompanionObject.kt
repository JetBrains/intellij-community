// PROBLEM: none
interface Foo {
    companion object : Foo
}

fun bar() {
    var value: Foo<caret> = Foo
    value = object : Foo {}
}
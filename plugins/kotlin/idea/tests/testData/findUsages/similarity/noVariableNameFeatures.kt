class FooClass {
    public fun do<caret>Foo(a:FooClass){}
}
fun test() {
    var a = FooClass()
    var b = FooClass()
    print(a.doFoo(a))
    print(b.doFoo(b))
}
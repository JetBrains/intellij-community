fun main(args: Array<String>) {
    val p: Foo = Foo() // simple class usage

    // companion object usages
    Foo.f()
    val x = Foo

    Foo.Bar.f()
    val xx = Foo.Bar
}
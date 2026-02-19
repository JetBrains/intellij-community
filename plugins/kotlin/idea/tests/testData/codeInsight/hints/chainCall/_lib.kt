class Foo {
    var foo = Foo()
    var bar = Bar()
    fun foo(block: () -> Unit = {}) = Foo()
    fun nullFoo(): Foo? = null
    fun bar(block: () -> Unit = {}) = Bar()
    fun nullBar(): Bar? = null
    operator fun inc() = Foo()
    operator fun get(index: Int) = Bar()
    operator fun invoke() = Bar()
}

class Bar {
    var foo = Foo()
    var bar = Bar()
    fun foo(block: () -> Unit = {}) = Foo()
    fun nullFoo(): Foo? = null
    fun bar(block: () -> Unit = {}) = Bar()
    fun nullBar(): Bar? = null
    operator fun inc() = Bar()
    operator fun get(index: Int) = Foo()
    operator fun invoke() = Foo()
}
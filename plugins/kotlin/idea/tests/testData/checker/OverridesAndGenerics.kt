    interface MyInterface<T> {
        fun foo(t: T) : T
    }

    abstract class MyAbstractClass<T> {
        abstract fun bar(t: T) : T
    }

    open class MyGenericClass<T> : MyInterface<T>, MyAbstractClass<T>() {
        override fun foo(t: T) = t
        override fun bar(t: T) = t
    }

    class MyChildClass : MyGenericClass<Int>() {}
    class MyChildClass1<T> : MyGenericClass<T>() {}
    class MyChildClass2<T> : MyGenericClass<T>() {
        fun <error>foo</error>(t: T) = t
        override fun bar(t: T) = t
    }

    open class MyClass : MyInterface<Int>, MyAbstractClass<String>() {
        override fun foo(t: Int) = t
        override fun bar(t: String) = t
    }

    <error>class MyIllegalGenericClass1</error><T> : MyInterface<T>, MyAbstractClass<T>() {}
    <error>class MyIllegalGenericClass2</error><T, R> : MyInterface<T>, MyAbstractClass<R>() {
        <error>override</error> fun foo(r: R) = r
    }
    <error>class MyIllegalClass1</error> : MyInterface<Int>, MyAbstractClass<String>() {}

    <error>class MyIllegalClass2</error><T> : MyInterface<Int>, MyAbstractClass<Int>() {
        fun foo(t: T) = t
        fun bar(t: T) = t
    }

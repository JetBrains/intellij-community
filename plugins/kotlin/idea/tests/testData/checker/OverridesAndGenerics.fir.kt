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
        fun foo(t: T) = t
        override fun bar(t: T) = t
    }

    open class MyClass : MyInterface<Int>, MyAbstractClass<String>() {
        override fun foo(t: Int) = t
        override fun bar(t: String) = t
    }

    <error descr="[ABSTRACT_MEMBER_NOT_IMPLEMENTED]">class MyIllegalGenericClass1</error><T> : MyInterface<T>, MyAbstractClass<T>() {}
    <error descr="[ABSTRACT_MEMBER_NOT_IMPLEMENTED]">class MyIllegalGenericClass2</error><T, R> : MyInterface<T>, MyAbstractClass<R>() {
        override fun foo(r: R) = r
    }
    <error descr="[ABSTRACT_MEMBER_NOT_IMPLEMENTED]">class MyIllegalClass1</error> : MyInterface<Int>, MyAbstractClass<String>() {}

    <error descr="[ABSTRACT_MEMBER_NOT_IMPLEMENTED]">class MyIllegalClass2</error><T> : MyInterface<Int>, MyAbstractClass<Int>() {
        fun foo(t: T) = t
        fun bar(t: T) = t
    }

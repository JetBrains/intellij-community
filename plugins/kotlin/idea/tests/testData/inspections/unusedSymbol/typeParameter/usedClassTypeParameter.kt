class UsedClassTypeParameter<T>(t: T) {
    init {
        println(t)
    }
}

class UsedClassTypeParameter2<T> {
    fun test() {
        Foo(this)
    }

    private class Foo<U>(foo: UsedClassTypeParameter2<U>) {
        init {
            println(foo)
        }
    }
}

class UsedClassTypeParameter3<T>

fun <U> foo3(foo: UsedClassTypeParameter3<U>) {
    println(foo)
}

open class AnotherClass
class UsedClassTypeParameter4<T: AnotherClass> {
    fun foo() = InnerClass(this)
    class InnerClass<T: AnotherClass>(outerClass: UsedClassTypeParameter4<T>) {
        init {
            println(outerClass)
        }
    }
}

fun main(args: Array<String>) {
    println(args)
    UsedClassTypeParameter("")
    UsedClassTypeParameter2<Int>().test()
    foo3(UsedClassTypeParameter3<Int>())
    UsedClassTypeParameter4<AnotherClass>().foo()
}
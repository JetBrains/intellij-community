class UnusedClassTypeParameter<T>(p: String) {
    init {
        println(p)
    }
}

class UnusedClassTypeParameter2<T> {
    fun test() {
        Foo<String>(UnusedClassTypeParameter2())
    }

    private class Foo<U>(foo: UnusedClassTypeParameter2<U>) {
        init {
            println(foo)
        }
    }
}

class UnusedClassTypeParameter3<T>

fun <U> foo3(foo: UnusedClassTypeParameter3<U>) {
    println(foo)
}

class UnusedClassTypeParameter4<T> {
    fun test() {
        Foo(this)
    }

    class Foo(foo: UnusedClassTypeParameter4<*>) {
        init {
            println(foo)
        }
    }
}


fun main(args: Array<String>) {
    println(args)
    UnusedClassTypeParameter("")
    UnusedClassTypeParameter2<Int>().test()
    foo3<Int>(UnusedClassTypeParameter3())
    UnusedClassTypeParameter4<Int>().test()
}
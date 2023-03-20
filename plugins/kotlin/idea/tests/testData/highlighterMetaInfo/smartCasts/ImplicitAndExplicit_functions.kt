interface Bar

interface Foo {
    fun Bar.regularFun()

    fun Bar?.nullableFun()
}

fun Any.foo(parameter: Any?) {

    if (this is Foo && parameter is Bar?) {

        parameter?.regularFun()

        parameter.nullableFun()

    }
}
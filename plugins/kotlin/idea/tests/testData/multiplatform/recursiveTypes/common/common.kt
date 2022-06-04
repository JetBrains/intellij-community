package sample

expect interface <!LINE_MARKER("descr='Has actuals in jvm module'"), LINE_MARKER("descr='Is subclassed by B  Click or press ... to navigate'")!>A<!><T : A<T>> {
    fun <!LINE_MARKER("descr='Has actuals in jvm module'")!>foo<!>(): T
}

interface B : A<B>

fun test(a: A<*>) {
    a.foo()
    a.foo().foo()
}

fun test(b: B) {
    b.foo()
    b.foo().foo()
}

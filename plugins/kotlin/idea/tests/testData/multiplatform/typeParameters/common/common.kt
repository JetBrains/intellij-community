@file:Suppress("NO_ACTUAL_FOR_EXPECT")

package foo

expect interface <!LINE_MARKER("descr='Has actuals in jvm module'"), LINE_MARKER("descr='Is subclassed by AImpl (foo) Press ... to navigate'")!>A<!> {
    fun <!LINE_MARKER("descr='Has actuals in jvm module'"), LINE_MARKER("descr='Is overridden in AImpl (foo) Press ... to navigate'")!>commonFun<!>()
}

class CommonGen<T : A> {
    val a: T get() = null!!
}

class List<out T>(val value: T)

fun getList(): List<A> = null!!

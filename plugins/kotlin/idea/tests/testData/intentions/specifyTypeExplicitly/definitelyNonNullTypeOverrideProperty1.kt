interface Base<T> {
    fun notNullFun(t: T): T & Any
}

class Test<T> : Base<T> {
    override fun <caret>notNullFun(t: T) = t!!
}

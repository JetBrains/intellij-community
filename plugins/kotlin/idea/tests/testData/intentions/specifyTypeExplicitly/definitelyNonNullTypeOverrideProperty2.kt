interface Base<T> {
    val notNullFun: T & Any
}

class Test<T>(t: T) : Base<T> {
    override val <caret>notNullFun = t!!
}

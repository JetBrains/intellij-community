class TypeWithTypeParam<E> {
    var value: String = ""
}

operator fun <T> TypeWithTypeParam<T>.plusAssign(x: T) {
    value += x.toString()
}

fun foo(x: Int, y: Long?) {
    val foo: TypeWithTypeParam<Number?> = TypeWithTypeParam()
    <caret>when (x) {
        3 -> foo += x
        4 -> foo += y
        else -> foo += null
    }
}
class TypeWithTypeParam<E> {
    var value: String = ""
}

operator fun <T> TypeWithTypeParam<T>.plusAssign(x: T) {
    value += x.toString()
}

fun foo(x: Int, y: Int?) {
    var foo: TypeWithTypeParam<Int?> = TypeWithTypeParam()
    <caret>if (x > 3) foo += x
    else foo += y
}
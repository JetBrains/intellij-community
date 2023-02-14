interface Transformer {
    fun transform(x: Int = 0, f: (Int) -> Int) = f(x)
}

class TransformerImpl : Transformer

fun bar(x: Int) = x * x

val y = TransformerImpl().transform { <caret>bar(it) }

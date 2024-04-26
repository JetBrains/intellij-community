class Klass: Iterable<String> {
    override fun iterator(): Iterator<String> {
        throw UnsupportedOperationException()
    }
}

fun main(args: Array<String>) {
    println(args)
    Klass()
}

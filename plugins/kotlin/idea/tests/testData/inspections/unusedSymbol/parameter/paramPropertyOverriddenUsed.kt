open class Klass {
    open val used = ":)"
}

class Subklass(override val used: String): Klass

fun main(args: Array<String>) {
    println(args)
    Subklass().used
}
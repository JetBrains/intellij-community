open class Klass {
    val used = ":)"
}

class Subklass: Klass()

fun main(args: Array<String>) {
    println(args)
    Subklass().used
}
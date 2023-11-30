open class Klass {
    open fun used() {
    }
}

class Subklass: Klass() {
    override fun used() {
    }
}

fun main(args: Array<String>) {
    println(args)
    Subklass().used()
}
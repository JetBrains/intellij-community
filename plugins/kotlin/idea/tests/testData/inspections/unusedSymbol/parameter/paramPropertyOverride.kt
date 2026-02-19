interface Interface {
    val member: String
}

class Klass(override val member: String)

fun main(args: Array<String>) {
    println(args)
    val t: Interface = Klass()
    println(t.member)
}

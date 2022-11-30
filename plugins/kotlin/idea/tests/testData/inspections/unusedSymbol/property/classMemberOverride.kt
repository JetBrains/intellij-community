interface Interface {
    val member: String
}

class Klass: Interface {
    override val member: String = ":)"
}

fun main(args: Array<String>) {
    val t: Interface = Klass()
    println(t.member)
}

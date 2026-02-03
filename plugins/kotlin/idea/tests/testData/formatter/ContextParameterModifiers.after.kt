interface Base {
    context(c: Int)
    val name: String

    context(c: Int)
    fun description()
}

interface Action : Base {
    context(c: Int)
    override val name: String
        get() = TODO("Not yet implemented")

    context(c: Int)
    override fun description() {
    }

    context(c: Int)
    operator fun invoke()

    context(c: Int)
    suspend fun test()

    context(c: Int)
    private val y: String
}

abstract class Impl : Action {
    context(c: Int)
    lateinit var x: String
}

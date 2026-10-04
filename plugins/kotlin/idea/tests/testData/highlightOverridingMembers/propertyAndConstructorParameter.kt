interface <info descr="null">WithProperties</info> {
    val x: Int
    val y: Int
    fun f()
}

interface Other {
    val z: Int
}

class Impl(override val <info descr="null">x</info>: Int, override val z: Int) : <info descr="null">~WithProperties</info>, Other {
    override val <info descr="null">y</info>: Int
        get() = 1

    override fun <info descr="null">f</info>() {
    }
}

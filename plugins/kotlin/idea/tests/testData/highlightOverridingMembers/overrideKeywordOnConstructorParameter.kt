interface WithProperties {
    val x: Int
}

interface Other {
    val z: Int
}

class Impl(~override val <info descr="null">x</info>: Int, override val z: Int) : <info descr="null">WithProperties</info>, Other

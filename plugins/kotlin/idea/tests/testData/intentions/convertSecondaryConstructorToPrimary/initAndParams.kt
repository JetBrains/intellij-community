class InitAndParams {
    constructor<caret>(x: Int, z: Int) {
        this.y = x
        w = foo(y)
        this.v = w + z
    }

    val y: Int

    val w: Int

    fun foo(arg: Int) = arg

    val v: Int
}

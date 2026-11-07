interface IFace1 {
    fun getIntValue(): Int
}

interface IFace2 {
    fun getStringValue(): String?
}

abstract class <info descr="null">AbstractMyClass</info> {
    abstract fun doSmth()
}

class MyKClass : <info descr="null">~AbstractMyClass</info>(), IFace1, IFace2 {
    override fun <info descr="null">doSmth</info>() {
    }

    override fun getIntValue(): Int {
        return 0
    }

    override fun getStringValue(): String? {
        return null
    }
}

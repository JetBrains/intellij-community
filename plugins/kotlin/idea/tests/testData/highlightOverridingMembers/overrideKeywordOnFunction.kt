interface IFace1 {
    fun getIntValue(): Int
}

interface IFace2 {
    fun getStringValue(): String?
}

abstract class AbstractMyClass {
    abstract fun doSmth()
}

class MyKClass : AbstractMyClass(), <info descr="null">IFace1</info>, IFace2 {
    override fun doSmth() {
    }

    ~override fun <info descr="null">getIntValue</info>(): Int {
        return 0
    }

    override fun getStringValue(): String? {
        return null
    }
}

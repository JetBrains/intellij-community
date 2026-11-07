interface IFace1 {
    fun getIntValue(): Int
}

interface IFace2 {
    fun getStringValue(): String?
}

class MyKClass : <info descr="null">IFace1</info>, IFace2 {
    override fun <info descr="null">~getIntValue</info>(): Int {
        return 0
    }

    override fun getStringValue(): String? {
        return null
    }

    fun usage(): Int {
        return <info descr="null">getIntValue</info>()
    }
}

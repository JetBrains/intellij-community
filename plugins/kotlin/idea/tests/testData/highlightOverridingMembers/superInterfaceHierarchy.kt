interface IBase {
    fun base()
}

interface <info descr="null">IDerived</info> : IBase {
    fun derived()
}

class MyClass : <info descr="null">~IDerived</info> {
    override fun <info descr="null">base</info>() {
    }

    override fun <info descr="null">derived</info>() {
    }

    fun regular() {
    }
}

interface IBase {
    fun base()
}

interface IDerived : IBase {
    fun derived()
}

class MyClass : <info descr="null">IDerived</info> {
    ~override fun <info descr="null">base</info>() {
    }

    override fun derived() {
    }

    fun regular() {
    }
}

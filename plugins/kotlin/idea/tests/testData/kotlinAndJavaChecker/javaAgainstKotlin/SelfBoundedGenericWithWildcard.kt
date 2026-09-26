class K {
    companion object {
        @JvmStatic
        fun create(): Builder<*> = ConcreteBuilder()
    }
}

interface Builder<Self : Builder<Self>> {
    fun self(): Self
}

class ConcreteBuilder : Builder<ConcreteBuilder> {
    override fun self(): ConcreteBuilder = this
}
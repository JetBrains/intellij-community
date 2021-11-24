fun AI.A.bar() {
    setFoo("123")
    foo = "123"
    getFoo()
    foo
}

fun ba(k: K) {
    k.setFoo("123")
    k.foo = "123"
    k.getFoo()
    k.foo
}

class K : AI {
    override fun getFoo(): String = ""
    override fun setFoo(s: String) = Unit
}
fun AI.A.bar() {
    getFoo()
    foo
}

fun ba(k: K) {
    k.getFoo()
    k.foo
}

class K : AI {
    override fun getFoo(): String = ""
}
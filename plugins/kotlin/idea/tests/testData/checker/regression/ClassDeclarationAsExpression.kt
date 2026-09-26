val p = 1 < <error descr="[DECLARATION_IN_ILLEGAL_CONTEXT]">class A {
    fun f() {
        f()
    }
}</error>

fun f(<warning descr="[UNUSED_PARAMETER]">i</warning>: Int = 3 < <error descr="[DECLARATION_IN_ILLEGAL_CONTEXT]">class A {
    fun f() {
        f()
    }
}</error>) {

}
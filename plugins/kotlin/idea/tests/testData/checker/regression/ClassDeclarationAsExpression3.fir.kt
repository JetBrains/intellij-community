fun f(i: Int = 3 <error descr="[OVERLOAD_RESOLUTION_AMBIGUITY]"><</error> <error descr="[EXPRESSION_EXPECTED]">class A {
    fun f() {
        f()
    }
}</error>) {

}

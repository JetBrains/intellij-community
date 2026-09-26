class A {
    inner class XYZ

    fun foo() {
        val v: A.XYZ = A.<error descr="[UNRESOLVED_REFERENCE]">XYZ</error>()
    }
}

class A {
    inner class XYZ

    fun foo() {
        val <warning>v</warning>: A.XYZ = A.<error descr="[RESOLUTION_TO_CLASSIFIER]">XYZ</error>()
    }
}
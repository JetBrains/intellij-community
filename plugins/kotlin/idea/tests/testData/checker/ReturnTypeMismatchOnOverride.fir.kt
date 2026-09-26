interface Base {
    fun foo(): Int
    var bar: Int
    val qux: Int
}

class Derived : Base {
    override fun foo(): <error descr="[RETURN_TYPE_MISMATCH_ON_OVERRIDE]">String</error> = ""
    override var bar: <error descr="[VAR_TYPE_MISMATCH_ON_OVERRIDE]">String</error> = ""
    override val qux: <error descr="[PROPERTY_TYPE_MISMATCH_ON_OVERRIDE]">String</error> = ""
}

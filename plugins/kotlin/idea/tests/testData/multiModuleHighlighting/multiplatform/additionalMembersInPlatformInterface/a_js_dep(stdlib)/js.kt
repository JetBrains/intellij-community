actual interface A
actual interface B {
    fun z()
    fun x()
}

<error descr="[ABSTRACT_MEMBER_NOT_IMPLEMENTED]">class Impl</error> : Both {
    override fun z() {
    }
}
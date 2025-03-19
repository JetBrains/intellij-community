interface AnonImplementation {
    fun firstFun<caret>()
}

val a = object : AnonImplementation {
    override fun firstFun() {
    }
}
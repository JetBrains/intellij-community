package a

abstract class Base {
    fun inheritedConcrete(): Int = 1
    abstract fun abstractMember()
}

class Test {
    companion object : Base() {
        override fun abstractMember() {}
    }
}

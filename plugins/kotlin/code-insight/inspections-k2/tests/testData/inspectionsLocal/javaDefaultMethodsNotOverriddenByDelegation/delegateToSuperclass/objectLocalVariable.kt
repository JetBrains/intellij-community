// FIX: Override Java default methods by explicit delegation to the superclass
class IImpl: Interface {
    override fun getInt(): Int = 42
}

fun test() {
    val iImpl = IImpl()
    object : Interface by iImpl<caret> {}
}

// FIX: Override Java default methods by delegation to the delegate object
class IImpl: Interface {
    override fun getInt(): Int = 42
}

fun test() {
    val iImpl = IImpl()
    object : Interface by iImpl<caret> {}
}

// FIX: Override Java default methods by explicit delegation to the superclass
// K2_ERROR: 'val' on function parameter is prohibited.
// K2_AFTER_ERROR: 'val' on function parameter is prohibited.
class IImpl: Interface {
    override fun getInt(): Int = 42
}

fun test(val iImpl: IImpl) {
    object : Interface by iImpl<caret> {}
}

// IS_APPLICABLE: true
// AFTER-WARNING: Variable 'x' is never used

class Owner(val z: Int) {
    fun foo(y: Int) = y + z
}

fun bar(owner: Owner) {
    val x = { arg: Int <caret> -> owner.foo(arg) }
}

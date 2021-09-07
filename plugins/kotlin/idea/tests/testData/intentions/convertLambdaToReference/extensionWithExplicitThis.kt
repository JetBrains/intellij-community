// IS_APPLICABLE: true

class Owner(val z: Int) {
    val x = { arg: Int <caret> -> this.foo(arg) }
}

fun Owner.foo(y: Int) = y + z


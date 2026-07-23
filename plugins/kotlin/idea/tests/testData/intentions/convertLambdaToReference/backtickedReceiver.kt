// IS_APPLICABLE: true

class Owner {
    fun foo(value: Int) = value
}

val `bar baz` = Owner()

val x = { value: Int <caret> -> `bar baz`.foo(value) }

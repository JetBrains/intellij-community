// "Change return type to 'Boolean'" "true"

interface A {
    fun foo(): Boolean
}

fun foo() {
    val x = object : A {
        override fun foo(): Boolean?<caret> = true
    }
}

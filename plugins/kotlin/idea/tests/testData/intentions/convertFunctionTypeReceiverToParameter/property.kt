// AFTER-WARNING: Parameter 'any' is never used
// AFTER-WARNING: The expression is unused
interface E {
    val foo: Stri<caret>ng.() -> Unit
}

class B : E {
    override val foo: String.() -> Unit = { doSmth(this) }
}

class C : E {
    override val foo: String.() -> Unit = { this.doSmth2() }
        get() = {
            field
            doSmth(this)
        }
}

class D : E {
    override val foo: String.() -> Unit
        get() {
            if (true) {
                return { doSmth2() }
            }

            return { doSmth(this) }
        }
}

fun doSmth(any: Any) {

}

fun <T> T.doSmth2() {

}
class T

open class A {
    fun T.fooExtension() {}
    val T.fooProperty get() = 10
}
object AOBject : A()

fun usage(t: T) {
    t.foo<caret>
}

// EXIST: { lookupString: "fooExtension", itemText: "fooExtension" }
// EXIST: { lookupString: "fooProperty", itemText: "fooProperty" }
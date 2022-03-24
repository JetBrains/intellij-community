class T

open class A {
    fun T.fooExtension() {}
    val T.fooProperty get() = 10
}
object AOBject : A()

fun usage(t: T) {
    t.foo<caret>
}

// EXIST: { lookupString: "fooExtension", itemText: "fooExtension", icon: "nodes/function.svg"}
// EXIST: { lookupString: "fooProperty", itemText: "fooProperty", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}
open class A {
    fun <T> T.fooExtension() {}
    val <T> T.fooProperty get() = 10
}

object AOBject : A()

class B

fun usage(arg: B) {
    arg.foo<caret>
}

// EXIST: { lookupString: "fooExtension", itemText: "fooExtension", icon: "nodes/function.svg"}
// EXIST: { lookupString: "fooProperty", itemText: "fooProperty", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}
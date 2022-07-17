class T

object A {
    fun T.fooExtension() {}
    val T.fooProperty get() = 10
}

fun usage(t: T) {
    t.foo<caret>
}

// EXIST: { lookupString: "fooExtension", itemText: "fooExtension", icon: "nodes/function.svg"}
// EXIST: { lookupString: "fooProperty", itemText: "fooProperty", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}
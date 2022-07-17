class T

class A {
    companion object {
        fun T.fooExtension() {}
        val T.fooProperty get() = 10
    }
}

fun usage(t: T) {
    t.<caret>
}

// EXIST: { lookupString: "fooExtension", itemText: "fooExtension", icon: "nodes/function.svg"}
// EXIST: { lookupString: "fooProperty", itemText: "fooProperty", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}
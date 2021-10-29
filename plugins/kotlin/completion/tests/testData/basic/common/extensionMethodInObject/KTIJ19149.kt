open class A {
    fun String.fooInherited() {}
}

object AObject : A() {
    fun String.fooExplicit() {}
}

fun usage() {
    "".foo<caret>
}

// EXIST: { "lookupString": "fooExplicit", "itemText": "fooExplicit", icon: "nodes/function.svg"}
// EXIST: { "lookupString": "fooInherited", "itemText": "fooInherited", icon: "nodes/function.svg"}

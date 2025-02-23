class Tree(parentTree: Tree? = null) {

    val foo: Any? = parentTree?.f<caret>
}

// INVOCATION_COUNT: 0
// ABSENT: foo
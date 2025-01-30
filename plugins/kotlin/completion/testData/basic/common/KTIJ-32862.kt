// IGNORE_K1
// IGNORE_K2

class Tree(parentTree: Tree? = null) {

    val foo: Any? = parentTree?.f<caret>
}

// INVOCATION_COUNT: 0
// EXIST: foo
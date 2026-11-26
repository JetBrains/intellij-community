fun red(): Int {
    val a = <caret>unknownCall(1 + 2)
    return a
}

// K2_TYPE: unknownCall(1 + 2) -> Type is unknown

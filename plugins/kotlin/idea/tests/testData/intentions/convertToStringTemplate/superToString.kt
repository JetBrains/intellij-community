class C {
    override fun toString(): String =
        <caret>super.toString() + " and more!"
}

// ERROR: The feature "multi platform projects" is experimental and should be enabled explicitly

expect class With<caret>Constructor(x: Int, s: String) {
    val x: Int
    val s: String

    override fun hashCode(): Int
}
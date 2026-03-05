// PROBLEM: none
interface Base {
    val value: String
}

sealed class Result<caret> : Base {
    final override val value: String = "test"
}
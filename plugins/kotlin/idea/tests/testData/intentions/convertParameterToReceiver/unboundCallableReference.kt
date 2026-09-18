fun foo(<caret>s: String, n: Int): Boolean = true

val ref: (String, Int) -> Boolean = ::foo
// "Replace with 'B<String>'" "true"
// WITH_STDLIB

@Deprecated(message = "renamed", replaceWith = ReplaceWith("B<E>"))
typealias A<E> = List<E>

typealias B<E> = List<E>

val x: <caret>A<String> = emptyList()

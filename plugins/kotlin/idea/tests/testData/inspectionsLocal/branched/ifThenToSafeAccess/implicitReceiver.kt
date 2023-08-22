// WITH_STDLIB
fun String?.foo() = <caret>if (this == null) null else isEmpty()

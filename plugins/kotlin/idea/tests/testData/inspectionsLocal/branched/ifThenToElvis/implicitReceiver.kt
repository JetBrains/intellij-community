// WITH_STDLIB
fun String?.foo() = <caret>if (this == null) true else isEmpty()
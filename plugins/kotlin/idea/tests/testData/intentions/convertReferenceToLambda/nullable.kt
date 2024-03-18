fun Int?.foo() = this?.hashCode() ?: 0

val x = <caret>Int?::foo

// IGNORE_K2
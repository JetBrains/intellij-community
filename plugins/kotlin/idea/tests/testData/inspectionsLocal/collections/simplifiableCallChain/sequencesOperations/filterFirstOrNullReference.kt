// WITH_STDLIB

val x = sequenceOf("1", "").<caret>filter(String::isNotEmpty).firstOrNull()
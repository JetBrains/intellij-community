// WITH_STDLIB

val nullableSequence: Sequence<String>? = sequenceOf("1", "")
val x = nullableSequence?.<caret>filter { it.isNotEmpty() }?.first()
// WITH_STDLIB

val x = sequenceOf("1", "").filte<caret>r { element -> element.isNotEmpty() }.last()
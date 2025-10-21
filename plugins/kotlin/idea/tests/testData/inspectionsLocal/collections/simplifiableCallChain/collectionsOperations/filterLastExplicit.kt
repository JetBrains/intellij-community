// WITH_STDLIB

val x = listOf("1", "").filte<caret>r { element -> element.isNotEmpty() }.last()
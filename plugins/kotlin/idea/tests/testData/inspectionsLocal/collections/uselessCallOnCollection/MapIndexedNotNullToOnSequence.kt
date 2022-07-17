// WITH_STDLIB

val someSeq = sequenceOf("alpha", "beta").<caret>mapIndexedNotNullTo(destination = hashSetOf()) { index, value -> index + value.length }
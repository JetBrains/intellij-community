fun testSetConcatenation(set1: Set<String>, set2: Set<String>, set3: Set<String>) {
    val result1 = set1 + set2
    val result2 = set1 + set2 + set2
}

fun collectionConcatenation() {
    val list1 = listOf("foo", "bar")
    val list2 = listOf("q", "b")

    val result1 = list1 + list2 + listOf("a", "", "ccc")
}

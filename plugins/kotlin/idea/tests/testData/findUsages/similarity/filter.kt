fun f() {
    val numbersMap = mapOf("key1" to 1, "key2" to 2, "key3" to 3, "key11" to 11)

    var filteredMap = "";
    filteredMap = numbersMap.<caret>filter { (key, value) ->  value > 10 && key.endsWith("1")}
    filteredMap = numbersMap.filter { (key, value) -> key.endsWith("5") && value > 1}
    filteredMap = numbersMap.filter { (key, value) -> false }
    filteredMap = numbersMap.filter { (key, value) -> true }
    filteredMap = numbersMap.filter { (key, value) -> key.endsWith("1") }
    filteredMap = numbersMap.filter { (key, value) -> key.endsWith("1") }
    println(filteredMap)
}
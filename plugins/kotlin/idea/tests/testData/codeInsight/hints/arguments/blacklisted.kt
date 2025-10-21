fun foo() {
    listOf(1, 2)
    setOf(1, 2)
    arrayOf(1, 2)
    arrayOf("1", "2")
    mutableListOf("1", "2")
    mutableSetOf("1", "2")
    mapOf("1" to "2")
    mutableMapOf("1" to "2")
    require(true)
    requireNotNull(true)
    check(true)
    // blacklisted from java
    buildList {
        add("a")
    }
    println("Kodee")
    Pair("one", 2)
    Triple(1, 2, 3)
}
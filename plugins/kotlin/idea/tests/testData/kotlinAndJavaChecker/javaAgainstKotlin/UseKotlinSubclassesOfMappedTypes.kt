class KotlinComparableTest : Comparable<Int> {
    override fun compareTo(other: Int): Int {
        throw UnsupportedOperationException()
    }
}

class KotlinIterableInterfaceTest : Iterable<String> {
    override fun iterator(): Iterator<String> {
        throw UnsupportedOperationException()
    }
}
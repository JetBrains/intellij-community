// FIR_IDENTICAL
abstract class A : List<String> {
    <caret>
}

// MEMBER_K2: "forEach(action: Consumer<in String>?): Unit"
// MEMBER_K2: "<T : Any?> toArray(generator: IntFunction<Array<out T?>?>): Array<out T?>?"
// MEMBER_K2: "spliterator(): Spliterator<String?>"

// MEMBER_K1: "forEach(action: Consumer<in String!>!): Unit"
// MEMBER_K1: "toArray(generator: IntFunction<Array<(out) T!>!>!): Array<(out) T!>!"
// MEMBER_K1: "spliterator(): Spliterator<String!>"

// MEMBER: "size: Int"
// MEMBER: "contains(element: String): Boolean"
// MEMBER: "containsAll(elements: Collection<String>): Boolean"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "get(index: Int): String"
// MEMBER: "hashCode(): Int"
// MEMBER: "indexOf(element: String): Int"
// MEMBER: "isEmpty(): Boolean"
// MEMBER: "iterator(): Iterator<String>"
// MEMBER: "lastIndexOf(element: String): Int"
// MEMBER: "listIterator(): ListIterator<String>"
// MEMBER: "listIterator(index: Int): ListIterator<String>"
// MEMBER: "parallelStream(): Stream<String>"
// MEMBER: "stream(): Stream<String>"
// MEMBER: "subList(fromIndex: Int, toIndex: Int): List<String>"
// MEMBER: "toString(): String"
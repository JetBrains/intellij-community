// FIR_IDENTICAL
// WITH_STDLIB
// MIN_JAVA_VERSION: 17
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY

// FILE: usage.kt
fun main() {
    val record1 = JavaRecord("", 32)
    val record2 = JavaRecord(4L, "str")
    val string: String = record1.string()
    val string2: String = record1.string
    val number: Int = record1.number()
    val number2: Int = record1.number
}

// FILE: JavaRecord.java
public record JavaRecord(String string, int number) {
    public JavaRecord(Long number, String string) {
        this(string, 4)
    }
}
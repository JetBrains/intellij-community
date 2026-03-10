class MyException : RuntimeException()

fun test() {
    throw <caret>
}

// EXIST: MyException
// EXIST: { itemText: "RuntimeException", tailText: "(...) (kotlin)" }
// EXIST: { itemText: "IllegalArgumentException", tailText: "(...) (kotlin)" }
// ABSENT: { itemText: "RuntimeException", tailText: "(...) (java.lang)" }
// ABSENT: { itemText: "IllegalArgumentException", tailText: "(...) (java.lang)" }

// IGNORE_K1

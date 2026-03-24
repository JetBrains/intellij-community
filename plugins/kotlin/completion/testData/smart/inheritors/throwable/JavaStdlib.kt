fun test() {
    throw <caret>
}

// EXIST: { itemText: "RuntimeException", tailText: "(...) (kotlin)" }
// EXIST: { itemText: "IllegalArgumentException", tailText: "(...) (kotlin)" }
// EXIST: { itemText: "IllegalStateException", tailText: "(...) (kotlin)" }
// EXIST: { itemText: "NullPointerException", tailText: "(...) (kotlin)" }
// EXIST: { itemText: "UnsupportedOperationException", tailText: "(...) (kotlin)" }
// EXIST: { itemText: "StackOverflowError", tailText: "(...) (java.lang)" }
// ABSENT: { itemText: "RuntimeException", tailText: "(...) (java.lang)" }
// ABSENT: { itemText: "IllegalArgumentException", tailText: "(...) (java.lang)" }
// ABSENT: { itemText: "IllegalStateException", tailText: "(...) (java.lang)" }
// ABSENT: { itemText: "NullPointerException", tailText: "(...) (java.lang)" }
// ABSENT: { itemText: "UnsupportedOperationException", tailText: "(...) (java.lang)" }

// IGNORE_K1

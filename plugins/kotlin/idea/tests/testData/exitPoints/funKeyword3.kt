fun test(s: String?): Int =
    if (s != null) {
        1~
    } else 0

// no exit point highlighting as to KTIJ-26395: we should not highlight exit points on the latest statement as it interferes with variable/call/type highlighting
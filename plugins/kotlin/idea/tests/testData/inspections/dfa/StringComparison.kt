// WITH_RUNTIME
fun compareStrings(s1: String, s2: String, s3: String) {
    if (<warning descr="Condition is always false">s1 > s1</warning>) {}
    if (s1 > s2) {
        if (<warning descr="Condition is always false">s1 <= s2</warning>) {}
    }
    if (s1 > s2) {
        if (s2 > s3) {
            if (<warning descr="Condition is always false">s3 > s1</warning>) {}
        }
    }
}
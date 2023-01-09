// IS_APPLICABLE: true
fun test(j: Java) {
    j.chained<caret>("test", 2).test(4, 8, 12)
}

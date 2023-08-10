// IS_APPLICABLE: true
fun test(j: Java) {
    j.chained("test", 2).test<caret>(4, 8, 12)
}

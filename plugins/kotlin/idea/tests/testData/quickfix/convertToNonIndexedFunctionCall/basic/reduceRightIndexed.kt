// "Convert to 'reduceRight'" "true"
// WITH_STDLIB
fun test(list: List<String>) {
    list.reduceRightIndexed { <caret>index, s, acc ->
        s + acc
    }
}
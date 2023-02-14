// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'map{}.map{}.firstOrNull{}'"
// INTENTION_TEXT_2: "Replace with 'asSequence().map{}.map{}.firstOrNull{}'"
// AFTER-WARNING: Variable 'result' is never used
fun foo(list: List<String>, o: Any) {
    if (o is CharSequence) {
        var result: Any? = null
        <caret>for (s in list) {
            val a = s.length + (o as String).replaceFirstChar(Char::titlecase).hashCode()
            val x = a * o.length
            if (x > 1000) {
                result = x
                break
            }
        }
    }
}
// WITH_STDLIB
// KTIJ-18352
fun process(options: Map<String, Int>, inputs: List<String>, s: String): List<Int> {
    val res = mutableListOf<Int>()
    var cur = -1
    for (str in inputs) {
        if (str.startsWith(s))
            if (options.containsKey(str)) {
                if (cur == -1) cur = options[str]!!
            } else if (options.containsKey(s+str)) {
                if (cur == -1) cur = if (<warning descr="Condition 'res.isEmpty()' is always true">res.isEmpty()</warning>) -1 else // no warning
                    res.removeAt(res.size - 1)
                if (cur != -1) res.add(cur + str.length)
            }
    }
    return res
}
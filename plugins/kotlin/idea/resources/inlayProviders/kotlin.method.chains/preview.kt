fun doSomething(list: List<Int>) {
    list.filter { it % 2 == 0 }/*<# List<Int> #>*/
        .map { it * 2 }
        .takeIf { list ->
            list.all { it % 2 == 0 }
        }/*<# List<Int>? #>*/
        ?.map { "item: $it" }/*<# List<String>? #>*/
        ?.forEach { println(it) }
}
// WITH_STDLIB
fun some(
    test1: Int? = null,
    test2: Int? = null,
) {
    if (test1 != null || test2 != null) {
        when {
            test2 != null -> {
            }
            <warning descr="Condition 'test1 == null' is always false">test1 == null</warning> -> {
            }
        }
        when {
            test2 != null -> {
            }
            test1 == null -> {
            }
            else -> {
                test1.toLong()
            }
        }
        when {
            test2 != null -> {
            }
            test1 == null -> {
            }
            test1.toLong() == 123L -> {
                
            }
            else -> {
                
            }
        }
        when {
            test2 != null -> {
            }
            test1 == null<error descr="[COMMA_IN_WHEN_CONDITION_WITHOUT_ARGUMENT] Deprecated syntax. Use '||' instead of commas in when-condition for 'when' without argument">,</error> test1.toLong() == 100L -> {
            }
        }
    }
}
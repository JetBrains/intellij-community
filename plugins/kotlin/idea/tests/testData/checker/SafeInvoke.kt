class Rule(val apply: () -> Unit)

fun foo() {
    val rule: Rule? = Rule { }
    rule?.<error descr="[UNSAFE_IMPLICIT_INVOKE_CALL]">apply</error>()
    val apply = rule?.apply
    <error descr="[UNSAFE_IMPLICIT_INVOKE_CALL]">apply</error>()
}

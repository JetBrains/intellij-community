// WITH_STDLIB
fun foo(): Boolean {
    val parentElement: <error descr="[UNRESOLVED_REFERENCE]">PsiElement</error>? = null

    if (<warning descr="Condition 'parentElement === \"\"' is always false">parentElement === ""</warning>)
        return false
    return true
}
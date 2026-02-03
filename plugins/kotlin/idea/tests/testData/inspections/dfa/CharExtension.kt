// WITH_STDLIB
fun Char.canBeStartOfIdentifierOrBlock(): Boolean {
    return isLetter() || this == '_' || this == '{' || this == '`'
}

fun Char.canBeStartOfIdentifierOrBlockIncorrect(): Boolean {
    return <warning descr="Condition 'isLetter() && this == '_' && this == '{' && this == '`'' is always false"><warning descr="Condition 'isLetter() && this == '_' && this == '{'' is always false">isLetter() && this == '_' && <warning descr="Condition 'this == '{'' is always false when reached">this == '{'</warning></warning> && this == '`'</warning>
}
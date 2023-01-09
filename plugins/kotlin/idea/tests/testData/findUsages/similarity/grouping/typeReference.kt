fun getB<caret>ar(): String {
    return ""
}
val a =getBar() as CharSequence
val b = getBar() as String
val c = getBar() as String

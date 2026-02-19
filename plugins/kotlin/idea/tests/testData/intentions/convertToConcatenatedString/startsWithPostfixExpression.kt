fun test(): String {
    var l = 0
    return "<caret>${l++}a$l"
}
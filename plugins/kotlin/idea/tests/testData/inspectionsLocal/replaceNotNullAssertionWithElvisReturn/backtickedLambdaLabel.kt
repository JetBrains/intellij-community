// WITH_STDLIB
fun test(list: List<String>, number: Int?) {
    list.forEach `foo bar`@{
        number!!<caret>
    }
}

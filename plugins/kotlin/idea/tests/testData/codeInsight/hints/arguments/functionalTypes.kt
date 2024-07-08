fun test(action: (String) -> Unit) {
    action("hello")
}

fun test2(action: (name: String) -> Unit) {
    // TODO: should be enabled when KTIJ-30438 is fixed
    // action(<# name|: #>"hello")
}
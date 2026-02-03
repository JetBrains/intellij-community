fun a() {
    when (true) {
        false -> Unit
        true
            <caret>-> Unit
    }
}
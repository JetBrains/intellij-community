fun test(x: Int) {
    <caret>x as? String //comment1
            ?: return
}
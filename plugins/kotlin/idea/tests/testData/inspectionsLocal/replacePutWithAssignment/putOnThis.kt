// WITH_STDLIB

class MyMap() : HashMap<String, String>() {
    init {
        this.<caret>put("foo", "bar")
    }
}
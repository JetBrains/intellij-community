val Any?.foo: Int
    get<caret>() {
        return 42
    }

public inline fun <T> T.let(block: (T) -> Unit) {}

//HIGHLIGHTED: get
//HIGHLIGHTED: return 42

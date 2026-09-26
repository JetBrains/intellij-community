// FIX: Remove 'val' from parameter
class UsedInInitializer(private <caret>val x: Int) {
    var y: String

    init {
        y = x.toString()
    }
}


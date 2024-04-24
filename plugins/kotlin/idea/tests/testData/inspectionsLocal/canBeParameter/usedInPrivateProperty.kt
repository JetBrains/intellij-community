// FIX: Remove 'val' from parameter
class PrivateUsedInProperty(private <caret>val x: Int) {
    val y = x
}
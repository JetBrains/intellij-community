data class Data(var quantity:Int)

fun increment(item:Data) {
    <caret>item.quantity = item.quantity + 1
}
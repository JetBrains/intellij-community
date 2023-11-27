package warehouse

import util.Utils

class Warehouse {
    // Fruit name to amount of it in warehouse
    private val entry: MutableMap<String, Int> = HashMap() // Apple, banana, etc...

    init {
        val availableFruits: Array<String> = Utils.FRUITS
        for (fruit in availableFruits) {
            entry[fruit] = 0
        }
    }

    /**
     * @param fruitName some fruit name from [Utils.FRUITS] (mango, apple...)
     */
    fun addFruits(fruitName: String, quantity: Int) {
        val curQuantity = entry[fruitName]
        if (curQuantity != null) {
            entry[fruitName] = curQuantity + quantity
        } else {
            throw IllegalArgumentException("Not found fruit with name: $fruitName")
        }
    }

    fun takeFruit(fruitName: String): Boolean {
        val curQuantity = entry[fruitName]
        requireNotNull(curQuantity) { "Not found fruit with name: $fruitName" }
        if (curQuantity > 0) {
            entry[fruitName] = curQuantity - 1
            return true
        }
        return false
    }

    fun printAllFruits() {
        for ((key, value) in entry) {
            println("$key: $value")
        }
    }
}
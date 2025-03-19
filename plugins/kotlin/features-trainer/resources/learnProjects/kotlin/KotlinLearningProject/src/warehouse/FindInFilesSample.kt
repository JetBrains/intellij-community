package warehouse

fun main() {
    val warehouse = Warehouse()
    warehouse.addFruits("peach", 3)
    warehouse.addFruits("pineapple", 5)
    warehouse.addFruits("mango", 1)
    warehouse.addFruits("apple", 5)
    val result = warehouse.takeFruit("apple")
    if (result) {
        println("This apple was delicious!")
    }
    warehouse.printAllFruits()
}
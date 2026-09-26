package a

data class Entry(val label: String) {
    fun getSize(): Int = label.length
}

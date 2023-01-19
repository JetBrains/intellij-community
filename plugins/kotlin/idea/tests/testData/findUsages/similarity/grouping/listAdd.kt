fun f() {
    val numbers = mutableListOf("one", "two", "three", "four")
    numbers.a<caret>dd ("five")
    numbers.add("zero")
    numbers.add(getStr())
    numbers.add(getStr() + "a")
    numbers.add(getStr() + "b")
}
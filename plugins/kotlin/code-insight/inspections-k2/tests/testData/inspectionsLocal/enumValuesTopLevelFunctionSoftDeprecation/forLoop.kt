enum class MyEnum {
    A, B, C
}

fun listAll()  {
    for (i in 0..<MyEnum.entries.size) {
        println(enumValues<caret><MyEnum>()[i])
    }
}

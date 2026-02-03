// PROBLEM: none
enum class OtherKtEnum {
    ONE, TWO
}

enum class MyKtEnum {
    <caret>ONE;

    init {
        val otherEnumValues = OtherKtEnum.values()
        val thisEnumValues = values()
    }
}
package testutils

object TestHelper {
    fun formatTestMessage(input: String): String {
        return "Test Message: ${input.trim().replace("\\s+".toRegex(), " ")}"
    }

    fun sumNumbers(vararg numbers: Int): Int {
        return numbers.sum()
    }

    fun reverseString(input: String): String {
        return input.lowercase().reversed()
    }

    fun isPalindrome(input: String): Boolean {
        return input.lowercase().replace("\\s+".toRegex(), "") == input.lowercase().replace("\\s+".toRegex(), "").reversed()
    }
}

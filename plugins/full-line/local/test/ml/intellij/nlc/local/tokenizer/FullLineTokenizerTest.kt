package ml.intellij.nlc.local.tokenizer

import ml.intellij.nlc.local.ModelsFiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream


internal class FullLineTokenizerTest {
    @ParameterizedTest
    @MethodSource("encodeTests")
    fun encode(testId: String, sentences: List<String>, expected_ids: List<List<Int>>) {
        val ids = bpe.encode(sentences, bos = false)
        assertEquals(expected_ids, ids)
    }

    @ParameterizedTest
    @MethodSource("decodeTests")
    fun decode(testId: String, ids: List<List<Int>>, expected_sentences: List<String>) {
        val sentences = bpe.decode(ids)
        assertEquals(expected_sentences, sentences)
    }

    companion object {
        val bpe = FullLineTokenizer(ModelsFiles.gpt2_py_6L_82_old_data.tokenizer, nThreads = 2)

        @JvmStatic
        fun encodeTests(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    "Leading spaces",
                    listOf("    def my_function():"),
                    listOf(listOf(4, 841, 880, 3562, 1409, 1550))
                ),
                Arguments.of(
                    "Leading newline",
                    listOf("\ndef my_function():"),
                    listOf(listOf(4, 880, 3562, 1409, 1550))
                ),
                Arguments.of(
                    "Leading spaces after newline",
                    listOf("\n    def my_function():"),
                    listOf(listOf(4, 841, 880, 3562, 1409, 1550))
                ),
                Arguments.of(
                    "Leading spaces before newline",
                    listOf("    \ndef my_function():"),
                    listOf(listOf(4, 841, 4, 880, 3562, 1409, 1550))
                ),
                Arguments.of(
                    "Trailing spaces",
                    listOf("    def my_function():  "),
                    listOf(listOf(4, 841, 880, 3562, 1409, 1550, 828))
                ),
                Arguments.of(
                    "Trailing newline",
                    listOf("    def my_function():\n"),
                    listOf(listOf(4, 841, 880, 3562, 1409, 1550, 4))
                ),
                Arguments.of(
                    "Trailing spaces after newline",
                    listOf("    def my_function():\n  "),
                    listOf(listOf(4, 841, 880, 3562, 1409, 1550, 4, 828))
                ),
                Arguments.of(
                    "Trailing spaces before newline",
                    listOf("    def my_function():  \n"),
                    listOf(listOf(4, 841, 880, 3562, 1409, 1550, 828, 4))
                ),
                Arguments.of(
                    "Multiple newlines",
                    listOf("\n\n\n    def my_function():\n\n\n"),
                    listOf(listOf(4, 841, 880, 3562, 1409, 1550, 4))
                ),
                Arguments.of(
                    "Different programming language",
                    listOf("fun testSquares(input: Int, expected: Int) {"),
                    listOf(listOf(4, 1159, 5, 930, 45, 960, 9, 925, 3802, 883, 1092, 1464, 3218, 883, 4266, 12004))
                ),
                Arguments.of(
                    "Not code, newlines and spaces",
                    listOf("abcd\n abcd\n efgh\n efgh"),
                    listOf(listOf(4, 4746, 17, 4, 5, 4746, 17, 4, 5, 2768, 2939, 4, 5, 2768, 2939))
                ),
                Arguments.of(
                    "Empty string",
                    listOf(""),
                    listOf(emptyList<Int>())
                ),
                Arguments.of(
                    "Just newline",
                    listOf("\n"),
                    listOf(listOf(4))
                ),
                Arguments.of(
                    "One character",
                    listOf("a"),
                    listOf(listOf(4, 9))
                ),
                Arguments.of(
                    "One space",
                    listOf(" "),
                    listOf(listOf(4, 5))
                ),
                Arguments.of(
                    "Unk non-ascii",
                    listOf("ἱερογλύφος"),
                    listOf(listOf(4, 1))
                ),
                Arguments.of(
                    "Non-ascii",
                    listOf("读5"),
                    listOf(listOf(4, 180, 71))
                ),
                Arguments.of(
                    "Two-byte utf",
                    listOf("读"),
                    listOf(listOf(4, 180))
                ),
                Arguments.of(
                    "Context split symbol (three-byte utf)",
                    listOf("₣"),
                    listOf(listOf(4, 90))
                ),
                Arguments.of(
                    "Multiple sentences",
                    listOf("123", "abc", "4567"),
                    listOf(listOf(4, 9898), listOf(4, 4746), listOf(4, 69, 4184, 85))
                ),

                Arguments.of(
                    "Fuzzy check",
                    listOf("*(^(*\\&^\"*%", "^@()@)@(*JDKJ>?>", "<><SCNAOodnaok∆∆ija9))"),
                    listOf(
                        listOf(
                            4, 63, 26, 102, 3665, 79, 101, 102, 22, 63, 65
                        ),
                        listOf(
                            4, 102, 84, 882, 84, 25, 84, 3665, 96, 55, 87, 96, 75, 98, 75
                        ),
                        listOf(
                            4, 86, 6954, 3297, 46, 48, 58, 13, 2858, 9, 1335, 1, 11, 6381, 83, 944
                        )
                    )
                ),
            )
        }

        @JvmStatic
        fun decodeTests(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    "Leading spaces",
                    listOf(listOf(4, 841, 880, 3562, 1409, 1550)),
                    listOf("\n    def my_function():")
                ),
                Arguments.of(
                    "Leading newline",
                    listOf(listOf(4, 880, 3562, 1409, 1550)),
                    listOf("\ndef my_function():")
                ),
                Arguments.of(
                    "Leading spaces after newline",
                    listOf(listOf(4, 841, 880, 3562, 1409, 1550)),
                    listOf("\n    def my_function():")
                ),
                Arguments.of(
                    "Leading spaces before newline",
                    listOf(listOf(4, 841, 4, 880, 3562, 1409, 1550)),
                    listOf("\n    \ndef my_function():")
                ),
                Arguments.of(
                    "Trailing spaces",
                    listOf(listOf(4, 841, 880, 3562, 1409, 1550, 828)),
                    listOf("\n    def my_function():  ")
                ),
                Arguments.of(
                    "Trailing newline",
                    listOf(listOf(4, 841, 880, 3562, 1409, 1550, 4)),
                    listOf("\n    def my_function():\n")
                ),
                Arguments.of(
                    "Trailing spaces after newline",
                    listOf(listOf(4, 841, 880, 3562, 1409, 1550, 4, 828)),
                    listOf("\n    def my_function():\n  ")
                ),
                Arguments.of(
                    "Trailing spaces before newline",
                    listOf(listOf(4, 841, 880, 3562, 1409, 1550, 828, 4)),
                    listOf("\n    def my_function():  \n")
                ),
                Arguments.of(
                    "Different programming language",
                    listOf(listOf(4, 1159, 5, 930, 45, 960, 9, 925, 3802, 883, 1092, 1464, 3218, 883, 4266, 12004)),
                    listOf("\nfun testSquares(input: Int, expected: Int) {")
                ),
                Arguments.of(
                    "Not code, newlines and spaces",
                    listOf(listOf(4, 4746, 17, 4, 5, 4746, 17, 4, 5, 2768, 2939, 4, 5, 2768, 2939)),
                    listOf("\nabcd\n abcd\n efgh\n efgh")
                ),
                Arguments.of(
                    "Empty string",
                    listOf(emptyList<Int>()),
                    listOf("")
                ),
                Arguments.of(
                    "Just newline",
                    listOf(listOf(4)),
                    listOf("\n")
                ),
                Arguments.of(
                    "One character",
                    listOf(listOf(4, 9)),
                    listOf("\na")
                ),
                Arguments.of(
                    "One space",
                    listOf(listOf(4, 5)),
                    listOf("\n ")
                ),
                Arguments.of(
                    "Unk non-ascii",
                    listOf(listOf(4, 1)),
                    listOf("\n<UNK>")
                ),
                Arguments.of(
                    "Non-ascii in string",
                    listOf(listOf(4, 180, 71)),
                    listOf("\n读5")
                ),
                Arguments.of(
                    "Two-byte utf",
                    listOf(listOf(4, 180)),
                    listOf("\n读")
                ),
                Arguments.of(
                    "Context split symbol (three-byte utf)",
                    listOf(listOf(4, 90)),
                    listOf("\n₣")
                ),
                Arguments.of(
                    "Multiple sentences",
                    listOf(listOf(4, 9898), listOf(4, 4746), listOf(4, 69, 4184, 85)),
                    listOf("\n123", "\nabc", "\n4567")
                ),
                Arguments.of(
                    "Without leading newline",
                    listOf(listOf(9898), listOf(4746), listOf(69, 4184, 85)),
                    listOf("123", "abc", "4567")
                ),
            )
        }
    }
}

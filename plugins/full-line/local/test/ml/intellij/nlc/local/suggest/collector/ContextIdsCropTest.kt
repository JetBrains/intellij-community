package ml.intellij.nlc.local.suggest.collector

import io.kinference.ndarray.toIntArray
import ml.intellij.nlc.local.LongLastLineException

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

internal class ContextIdsCropTest {

    @ParameterizedTest
    @MethodSource("no startIds, no minContextLen tests")
    fun `no startIds, no minContextLen`(
        testId: String, contextIds: IntArray, maxContextLen: Int, expectedCrop: IntArray
    ) {
        val croppedContextIds = BaseCompletionsGenerator.cropContextIds(contextIds, maxContextLen, null, null)
        assertEquals(expectedCrop.toList(), croppedContextIds.toList())
    }

    @ParameterizedTest
    @MethodSource("startIds, no minContextLen tests")
    fun `startIds, no minContextLen`(
        testId: String, contextIds: IntArray, maxContextLen: Int, expectedCrop: IntArray
    ) {
        val croppedContextIds = BaseCompletionsGenerator.cropContextIds(
            contextIds, maxContextLen, null, startIds
        )
        assertEquals(expectedCrop.toList(), croppedContextIds.toList())
    }

    @ParameterizedTest
    @MethodSource("no startIds, minContextLen tests")
    fun `no startIds, minContextLen`(
        testId: String, contextIds: IntArray, maxContextLen: Int, minContextLen: Int, expectedCrop: IntArray
    ) {
        val croppedContextIds = BaseCompletionsGenerator.cropContextIds(
            contextIds, maxContextLen, minContextLen, null
        )
        assertEquals(expectedCrop.toList(), croppedContextIds.toList())
    }

    @ParameterizedTest
    @MethodSource("startIds, minContextLen tests")
    fun `startIds, minContextLen`(
        testId: String, contextIds: IntArray, maxContextLen: Int, minContextLen: Int, expectedCrop: IntArray
    ) {
        val croppedContextIds = BaseCompletionsGenerator.cropContextIds(
            contextIds, maxContextLen, minContextLen, startIds
        )
        assertEquals(expectedCrop.toList(), croppedContextIds.toList())
    }

    @Test
    fun `throws long last line`() {
        val contextIds = ctx().lns(3, 2, 5)

        assertThrows<LongLastLineException> {
            BaseCompletionsGenerator.cropContextIds(contextIds, 3, null, listOf(0))
        }

        assertThrows<LongLastLineException> {
            BaseCompletionsGenerator.cropContextIds(contextIds, 4, null, listOf(0))
        }

        // Should not throw
        assertEquals(
            ctx().ln(5, 6).toList(), BaseCompletionsGenerator.cropContextIds(contextIds, 5, null, listOf(0)).toList()
        )
    }

    companion object {
        private val startIds = listOf(0)

        private fun getSingleTestArgs(
            cropPosition: Int, offset: Int, maxContextLen: Int, minContextLen: Int, contextIds: IntArray
        ): Arguments {
            assert(offset <= maxContextLen) { "In tests, offset should be less or equal to maxContextLen" }
            return Arguments.of(
                "From $cropPosition — Offset $offset/$maxContextLen (minContextLen = $minContextLen)",
                contextIds.copyOfRange(0, cropPosition + offset),
                maxContextLen,
                minContextLen,
                contextIds.copyOfRange(cropPosition, cropPosition + offset)
            )
        }

        @JvmStatic
        fun `no startIds, no minContextLen tests`(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    "Short context", ctx().n(5), 3, ctx().n(3, 2)
                ),
                Arguments.of(
                    "Medium context", ctx().n(500), 384, ctx().n(384, 116)
                ),
                Arguments.of(
                    "Long context", ctx().n(3000), 1000, ctx().n(1000, 2000)
                ),
                Arguments.of(
                    "Shorter contextIds", ctx().n(10), 20, ctx().n(10)
                ),
                Arguments.of(
                    "Empty contextIds", ctx(), 20, ctx()
                ),
                Arguments.of(
                    "One token context", ctx().n(10), 1, ctx().n(1, 9)
                ),
            )
        }

        @JvmStatic
        fun `startIds, no minContextLen tests`(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    "Simple test", ctx().lns(2, 3, 5), 5, ctx().ln(5, 6)
                ),
                Arguments.of(
                    "One line", ctx().ln(2), 3, ctx().ln(2)
                ),
            )
        }

        @JvmStatic
        fun `no startIds, minContextLen tests`(): Stream<Arguments> {
            val maxContextLen = 10
            val minContextLen = 6
            val fileStartOffsets: List<Int> = listOf(0, 1, 4, 5, 6, 7, 9, 10)
            val midFileOffsets: List<Int> = listOf(6, 7, 9, 10)
            val jumpNumbers: List<Int> = listOf(1, 2)

            val fileStartArgs = fileStartOffsets.map { offset ->
                val contextIds = ctx().n(offset)
                getSingleTestArgs(0, offset, maxContextLen, minContextLen, contextIds)
            }

            val midFileArgs = jumpNumbers.flatMap { jumpNumber ->
                midFileOffsets.map { offset ->
                    val cropPosition = minContextLen * jumpNumber
                    val contextIds = ctx().n(cropPosition + offset)
                    getSingleTestArgs(cropPosition, offset, maxContextLen, minContextLen, contextIds)
                }
            }
            return (fileStartArgs + midFileArgs).stream()
        }

        @JvmStatic
        fun `startIds, minContextLen tests`(): Stream<Arguments> {
            val maxContextLen = 10
            val minContextLen = 6

            // Note: zero is \n
            // 0 1 2 3 4 5                          // 0   (+6)  --
            // 0 7                                  // 6   (+2)  --
            // 0 9                                  // 8   (+2)
            // 0                                    // 10  (+1)
            // 0 12 13 14 15 16                     // 11  (+6)  --
            // 0 18 19 20 21 22 23                  // 17  (+7)  --
            // 0 25 26 27 28                        // 24  (+5)  --
            // 0 30                                 // 29  (+2)  --
            // 0 32 33                              // 31  (+3)
            // 0                                    // 34  (+1)  --
            // 0 36 37 38 39 40 41 42 43 44 45 46   // 35  (+12) --
            // 0 48 49 50 51 52 53 54 55 56         // 47  (+10) --
            // 0 58 59                              // 57  (+3)  --
            // 0 61 62 63                           // 60  (+4)  --
            // 0 65 66 67 68                        // 64  (+5)  --
            // 0 70                                 // 69  (+2)
            // 0                                    // 71  (+1)
            // 0 73 74                              // 72  (+3)
            val contextIds = ctx().lns(6, 2, 2, 1, 6, 7, 5, 2, 3, 1, 12, 10, 3, 4, 5, 2, 1, 3)

            val cropPositionToGlobalOffsets = listOf(
                0 to (0..10),
                6 to (11..16),
                11 to (17..21),
                17 to (22..27),
                24 to (28..34),
                29 to (35..39),
                34 to (40..44),
                35 to listOf(45),  // 46 47 LongLastLineException
                47 to (48..57),
                57 to (58..67),
                60 to (68..70),
                64 to (71..74)
            )

            return cropPositionToGlobalOffsets.flatMap { (cropPosition, globalOffsets) ->
                globalOffsets.map { globalOffset ->
                    getSingleTestArgs(
                        cropPosition, globalOffset - cropPosition, maxContextLen, minContextLen, contextIds
                    )
                }
            }.stream()
        }

        /** Initialize test context */
        private fun ctx(): IntArray {
            return intArrayOf()
        }

        /** Add `length` tokens to a test context */
        private fun IntArray.n(length: Int, startFrom: Int? = null): IntArray {
            val finalStartFrom = startFrom ?: this.size
            return this + (finalStartFrom until finalStartFrom + length).toIntArray()
        }

        /** Add one line of length `length` to a test context */
        private fun IntArray.ln(length: Int, startFrom: Int? = null): IntArray {
            assert(length >= 1) { "Length of a line must be >= 1, because it's at least \\n" }
            return (this + intArrayOf(0)).n(length - 1, startFrom)
        }

        /** Add multiple lines of lengths `lengths` to a test context */
        private fun IntArray.lns(vararg lengths: Int, startFrom: Int? = null): IntArray {
            val init = this.ln(lengths[0], startFrom)
            return if (lengths.size == 1) {
                init
            } else {
                lengths.copyOfRange(1, lengths.size).fold(init) { accumulator, element -> accumulator.ln(element) }
            }
        }
    }
}

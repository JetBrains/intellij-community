package org.jetbrains.idea.devkit.module.icons

import java.util.*

internal const val GRID_SIZE = 8

/**
 * Generates 8x8 boolean grid representing an icon pattern.
 * The pattern is vertically symmetric (mirrored left-to-right)
 * for a recognizable, logo-like appearance.
 */
internal data class IconData(
    val grid: Array<BooleanArray>,
    val foregroundColor: String,
    val backgroundColor: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IconData) return false
        return grid.contentDeepEquals(other.grid)
                && foregroundColor == other.foregroundColor
                && backgroundColor == other.backgroundColor
    }

    override fun hashCode(): Int {
        var result = grid.contentDeepHashCode()
        result = 31 * result + foregroundColor.hashCode()
        result = 31 * result + (backgroundColor?.hashCode() ?: 0)
        return result
    }
}

internal object IconGenerator {
    // Bias toward filling ~45% of cells for visual balance
    private const val FILL_THRESHOLD = 0.45

    /**
     * Generate an icon from a seed.
     *
     * @param seed           Deterministic seed for randomization
     * @param colorOverride  Optional hex color to use instead of auto-picked
     * @param background     Background color, or null for transparent
     * @param isDark         Whether to use dark theme colors
     */
    fun generate(
        seed: Long,
        colorOverride: String? = null,
        background: String? = null,
        isDark: Boolean = false,
    ): IconData {
        val random = Random(seed)

        val foreground = colorOverride ?: ColorPalette.pickColor(random, isDark)

        val grid = Array(GRID_SIZE) { BooleanArray(GRID_SIZE) }

        // Generate left half (columns 0..3), mirror to right half (columns 7..4)
        for (row in 0 until GRID_SIZE) {
            for (col in 0 until GRID_SIZE / 2) {
                val filled = random.nextDouble() < FILL_THRESHOLD
                grid[row][col] = filled
                grid[row][GRID_SIZE - 1 - col] = filled
            }
        }

        return IconData(
            grid = grid,
            foregroundColor = foreground,
            backgroundColor = background,
        )
    }
}

internal fun stringToSeed(input: String): Long {
  var hash = 0L
  for (char in input) {
    hash = (hash * 31 + char.code) and 0x7FFFFFFF
  }
  return hash
}
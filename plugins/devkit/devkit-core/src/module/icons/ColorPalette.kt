package org.jetbrains.idea.devkit.module.icons

import java.util.Random

internal object ColorPalette {
    val LIGHT_COLORS: Map<String, String> = mapOf(
        "red1" to "#DB5860",
        "red2" to "#C75450",
        "red3" to "#E85D75",
        "orange1" to "#EDA200",
        "orange2" to "#F0A732",
        "orange3" to "#D4972F",
        "green1" to "#59A869",
        "green2" to "#499C54",
        "green3" to "#3D8047",
        "blue1" to "#389FD6",
        "blue2" to "#3592C4",
        "blue3" to "#2B7FAF",
        "purple1" to "#B99BF8",
        "purple2" to "#9876AA",
        "purple3" to "#7B5FA1",
        "pink1" to "#F98B9E",
        "pink2" to "#E55C9C",
        "pink3" to "#CC4B87",
        "amber1" to "#FF8A47",
        "amber2" to "#E87A3E",
        "teal1" to "#20D5C0",
        "teal2" to "#1AB39E",
        "violet1" to "#967ADC",
        "violet2" to "#7D5FC2"
    )

    val DARK_COLORS: Map<String, String> = mapOf(
        "red1" to "#FF6B6B",
        "red2" to "#FF8787",
        "red3" to "#FFA5A5",
        "orange1" to "#FFC66D",
        "orange2" to "#FFD789",
        "orange3" to "#FFE5A8",
        "green1" to "#73C990",
        "green2" to "#8FDBA5",
        "green3" to "#A8E6BA",
        "blue1" to "#40B6E0",
        "blue2" to "#6AB9F2",
        "blue3" to "#8FCDFF",
        "purple1" to "#B99BF8",
        "purple2" to "#CDB4FF",
        "purple3" to "#E0CFFF",
        "pink1" to "#FF6B9D",
        "pink2" to "#FF8FB5",
        "pink3" to "#FFB3CC",
        "amber1" to "#FF9559",
        "amber2" to "#FFB086",
        "teal1" to "#4EEAD4",
        "teal2" to "#72F0DF",
        "violet1" to "#967ADC",
        "violet2" to "#B39AE8"
    )

    private val colorNames = LIGHT_COLORS.keys.toList()

    /**
     * Pick a color using a Random instance (for secondary color choices).
     */
    fun pickColor(random: Random, isDark: Boolean = false): String {
        val colorName = colorNames[random.nextInt(colorNames.size)]
        val colors = if (isDark) DARK_COLORS else LIGHT_COLORS
        return colors[colorName]!!
    }
}
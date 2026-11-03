//region Test configuration
// - hidden: line markers
//endregion
package sample

import kotlinx.datetime.LocalDate

class Event(val date: LocalDate)

fun firstDayOf2024(): LocalDate = LocalDate(2024, 1, 1)

fun format(date: LocalDate): String = date.toString()

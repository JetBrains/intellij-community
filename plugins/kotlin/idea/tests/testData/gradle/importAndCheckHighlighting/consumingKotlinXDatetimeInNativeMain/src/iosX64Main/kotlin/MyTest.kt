import kotlinx.datetime.*

actual fun <lineMarker descr="Has declaration in common module">f</lineMarker>(): LocalDate {
    val ld = LocalDate(0, 0, 0)
    ld.toNSDateComponents()
    return Failing().r()
}
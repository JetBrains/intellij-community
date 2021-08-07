import kotlinx.datetime.*

expect fun <lineMarker descr="Has actuals in Native (2 modules), JVM">f</lineMarker>(): LocalDate

fun g() {
    // println(f().toNSDateComponents())
}
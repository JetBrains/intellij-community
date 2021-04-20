import kotlinx.datetime.*
import platform.Foundation.*

class Failing {

    fun x(): NSDateComponents? {
        return null
    }

    fun ugh(): NSDateComponents = LocalDate(0, 0, 0).toNSDateComponents()

    fun r(): LocalDate {
        val ld = LocalDate(0, 0, 0)
        ld.toNSDateComponents()
        return ld
    }
}
// WITH_STDLIB
import java.math.BigDecimal

fun foo(decimal: BigDecimal?) {
    val d: BigDecimal? = BigDecimal(1)
    decimal <caret>== d
}
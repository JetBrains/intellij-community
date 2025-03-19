import java.math.BigDecimal

fun foo(decimal: BigDecimal?) {
    decimal <caret>== BigDecimal(1)
}
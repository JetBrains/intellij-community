import java.math.BigDecimal

fun foo(decimal: BigDecimal?) {
    BigDecimal(1) <caret>== decimal
}
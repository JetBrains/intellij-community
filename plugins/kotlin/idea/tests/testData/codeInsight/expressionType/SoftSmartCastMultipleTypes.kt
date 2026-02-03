interface A
interface B

fun fn(value: Any) {
    if (value is B) {
        if (value is A) {
            println(valu<caret>e)

        }
    }
}

// K1_TYPE: value -> <html>B & A (smart cast from Any)</html>

// K2_TYPE: value -> <b>B &amp; A</b><br/>smart cast from <b>Any</b>

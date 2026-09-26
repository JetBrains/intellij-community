interface A
interface B : A

fun fn(value: Any) {
    if (value is B) {
        if (<caret>value is A) { // here

        }
    }
}

// K1_TYPE: value -> <html>B (smart cast from Any)</html>
// K1_TYPE: value is A -> <html>Boolean</html>

// K2_TYPE: value -> <b>B</b><br/>smart cast from <b>Any</b>
// K2_TYPE: value is A -> <b>Boolean</b>

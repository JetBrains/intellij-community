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

// K2_TYPE: value -> B
// K2_TYPE: value is A -> Boolean

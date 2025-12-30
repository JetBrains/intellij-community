fun foo() {
    val <caret>x = 1
}

// K1_TYPE: val x = 1 -> <html>Int</html>

// K2_TYPE: val x = 1 -> <b>Int</b>

fun foo(value: String) {
    print<caret>ln(value)
}

// K1_TYPE: println(value) -> <html>Unit</html>

// K2_TYPE: println(value) -> <b>Unit</b>

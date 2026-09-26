class A {
    val x: String
        get() = "a<caret>bc"
}

// K1_TYPE: "abc" -> <html>String</html>

// K2_TYPE: "abc" -> <b>String</b>

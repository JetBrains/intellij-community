fun foo(x: Any) {
    if (x is String) {
        <caret>x.length
    }
}

// K1_TYPE: x -> <html>String (smart cast from Any)</html>
// K1_TYPE: x.length -> <html>Int</html>

// K2_TYPE: x -> String
// K2_TYPE: x.length -> Int

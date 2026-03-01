val x = if (2 > 1) <caret>3 else 4

// K1_TYPE: if (2 > 1) 3 else 4 -> <html>Int</html>

// K2_TYPE: if (2 > 1) 3 else 4 -> <b>Int</b>

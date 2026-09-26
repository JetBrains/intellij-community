val x = if (flag) {
    if (flag2) 1<caret>3 else 7
} else 42

// K1_TYPE: if (flag2) 13 else 7 -> <html>Int</html>

// K2_TYPE: if (flag2) 13 else 7 -> <b>Int</b>

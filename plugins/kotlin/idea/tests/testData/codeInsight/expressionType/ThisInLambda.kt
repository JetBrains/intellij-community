val x = 1.apply {
    th<caret>is
}

// K1_TYPE: this -> <html>Int</html>

// K2_TYPE: this -> <b>Int</b>
// K2_TYPE: {     this } -> <b>Int.() -&gt; Unit</b>
// K2_TYPE: 1.apply {     this } -> <b>Int</b>

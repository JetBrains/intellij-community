fun x(){
    val challenges = mutableListOf<() -> Unit>()
    for (c in challenges) {
        <caret>c()
    }
}

// K1_TYPE: c -> <html>() -&gt; Unit</html>
// K1_TYPE: c() -> <html>Unit</html>

// K2_TYPE: c -> () -&gt; Unit
// K2_TYPE: c() -> Unit

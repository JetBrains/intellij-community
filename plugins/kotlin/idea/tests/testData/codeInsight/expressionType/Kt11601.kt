fun x(){
    val challenges = mutableListOf<() -> Unit>()
    for (c in challenges) {
        <caret>c()
    }
}

// TYPE: c -> <html>() -&gt; Unit</html>
// TYPE: c() -> <html>Unit</html>

// some comment
fun summary(lines: List<String>) = li<caret>nes
    .map { it.trim() }
    .size

// K2_TYPE: lines -> <b>List&lt;String&gt;</b>
// K2_TYPE: lines     .map { it.trim() } -> <b>List&lt;String&gt;</b>
// K2_TYPE: lines     .map { it.trim() }     .size -> <b>Int</b>

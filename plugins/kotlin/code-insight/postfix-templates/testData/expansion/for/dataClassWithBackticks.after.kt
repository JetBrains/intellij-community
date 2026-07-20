data class Row(val `when`: Int, val value: Int)

fun test(rows: List<Row>) {
    for ((<selection>`when`<caret></selection>, value) in rows) {

    }
}
// MODE: return
val x = run {
    println(1)
    if (true) 1 else { 0 }/*<# ^|[file.kt:28]run #>*/
}
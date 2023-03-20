// "Inline type parameter" "true"
fun <T> test(p: T, q: T) where T : String<caret> = p.length + q.length
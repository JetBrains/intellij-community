// WITH_STDLIB
fun main() {
    if (<warning descr="Condition '1 == try { 1 } catch (e: Exception) { null }' is always true">1 == try { 1 } catch (e: Exception) { null }</warning>) {} 
}
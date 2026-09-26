// WITH_STDLIB
// PROBLEM: Java collection 'ConcurrentHashMap' is parameterized with a nullable type
// FIX: none
import java.util.concurrent.ConcurrentHashMap

val map = mutableMapOf<String?, String?>(null to null)
val chm = ConcurrentHashMap<caret>(map)
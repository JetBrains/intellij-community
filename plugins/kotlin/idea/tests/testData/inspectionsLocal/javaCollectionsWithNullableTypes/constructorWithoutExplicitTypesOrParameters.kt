// WITH_STDLIB
// PROBLEM: Java collection 'ConcurrentHashMap' is parameterized with a nullable type
// FIX: none
import java.util.concurrent.ConcurrentHashMap

val myMap: MutableMap<String?, String?> = Concurrent<caret>HashMap()
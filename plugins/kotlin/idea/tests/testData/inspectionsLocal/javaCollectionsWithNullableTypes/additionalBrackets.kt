// WITH_STDLIB
// PROBLEM: Java collection 'ConcurrentHashMap' is parameterized with nullable types
import java.util.concurrent.ConcurrentHashMap

val map = ConcurrentHashMap<(String?), ((<caret>String)?)>()
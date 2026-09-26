// WITH_STDLIB
// PROBLEM: Java collection 'ConcurrentLinkedDeque' is parameterized with a nullable type
// FIX: none
import java.util.concurrent.ConcurrentLinkedDeque

typealias Foo = String?

val deque: ConcurrentLinkedDeque<Foo<caret>> = ConcurrentLinkedDeque<Foo>()
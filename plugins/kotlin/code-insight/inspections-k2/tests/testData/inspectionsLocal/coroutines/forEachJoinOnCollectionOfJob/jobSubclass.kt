// WITH_COROUTINES
// PROBLEM: Usage of 'forEach { it.join() }' on 'Collection<Job>' instead of single 'joinAll()'
import kotlinx.coroutines.Deferred

suspend fun test(jobs: List<Deferred<String>>) {
    jobs.<caret>forEach { it.join() }
}

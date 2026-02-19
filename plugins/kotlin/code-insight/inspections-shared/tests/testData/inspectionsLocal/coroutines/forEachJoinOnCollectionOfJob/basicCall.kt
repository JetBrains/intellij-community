// WITH_COROUTINES
// PROBLEM: Usage of 'forEach { it.join() }' on 'Collection<Job>' instead of single 'joinAll()'
import kotlinx.coroutines.Job

suspend fun test(jobs: List<Job>) {
    jobs.<caret>forEach { it.join() }
}

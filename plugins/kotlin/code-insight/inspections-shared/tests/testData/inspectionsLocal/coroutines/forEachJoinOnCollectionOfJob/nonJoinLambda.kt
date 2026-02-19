// WITH_COROUTINES
// PROBLEM: none
import kotlinx.coroutines.Job

suspend fun test(jobs: List<Job>) {
    jobs.<caret>forEach { it.join().toString() }
}

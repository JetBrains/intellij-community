// WITH_COROUTINES
// PROBLEM: none
import kotlinx.coroutines.Job

suspend fun test(jobs: List<Job>, otherJob: Job) {
    val otherObject = Job()
    jobs.<caret>forEach { otherJob.join() }
}

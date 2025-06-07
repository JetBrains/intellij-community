// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.Job

suspend fun takeArray(jobArray: Array<Job>) {
    jobArray.<caret>forEach { it.join() }
}
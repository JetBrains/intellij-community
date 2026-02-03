// WITH_STDLIB
// FIX: Convert to 'let'
// IGNORE_K1
class Repository {
    val data = "repo-data"

    inner class Processor {
        val processorName = "processor"

        fun execute(user: User) {
            <caret>with(user) {
                // Multiple 'this' contexts: Repository and inner Processor class
                println("Processing $name by ${this@Repository.data} using ${this@Processor.processorName}")
            }
        }
    }
}

class User(val name: String)

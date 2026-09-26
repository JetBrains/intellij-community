// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

class Logger
class Database

context(l: Logger, db: Database)
fun saveWithLog() {}

fun test(logger: Logger, database: Database) {
    context(logger, database) {
        saveWithLog(<caret>db = database, l = logger)
    }
}
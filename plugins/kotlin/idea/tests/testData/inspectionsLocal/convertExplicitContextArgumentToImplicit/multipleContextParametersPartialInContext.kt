// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

class Logger
class Database

context(l: Logger, db: Database)
fun saveWithLog() {}

context(logger: Logger)
fun test(database: Database) {
    saveWithLog(<caret>l = logger, db = database)
}
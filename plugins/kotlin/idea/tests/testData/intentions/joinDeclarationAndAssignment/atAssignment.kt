// AFTER-WARNING: Parameter 's' is never used
// AFTER-WARNING: Variable 'hash' is never used
fun test(repo: Repository, commitMessage: String) {
    val hash: String<caret>
    repo.git("add --verbose .")
    hash = repo.git("commit -m $commitMessage")
}

class Repository {
    fun git(s: String) = ""
}
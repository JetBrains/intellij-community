// COMPILER_ARGUMENTS: -Xcontext-parameters

context(ctx: A) fun <A> implicit():A = ctx

interface Config {
    val debug: Boolean
}

context(<caret>_: Config)
fun isDebug(bool: Boolean = implicit<Config>().debug): Boolean {
    return bool
}

package com.intellij.buildsystem.model

/**
 * Manipulates dependencies of type [D] and repositories of type [R] for a build script.
 * Implementations are tied to a single build system (e.g., Gradle).
 */
interface BuildManager<D : BuildDependency, R : BuildDependencyRepository> {

    /**
     * Performs a batch of operations and only writes changes back to the backing
     * file or memory buffer when they are all done. Note that any operation failing
     * in the batch will prevent the writeback from happening, thus this is an atomic
     * operation.
     *
     * Note that the operations are performed in this order:
     *  1. Remove dependencies
     *  2. Remove repositories
     *  3. Add dependencies
     *  4. Add repositories
     *  5. Write back changes to backing buffer/file
     *
     * But the order in which items are added and removed within each set is NOT
     * deterministic.
     *
     * @param removeDependencies a set of [BuildDependency] to remove
     * @param removeRepositories a set of [BuildDependencyRepository] to remove
     * @param addDependencies a set of [BuildDependency] to add
     * @param addRepositories a set of [BuildDependencyRepository] to add
     * @return a list of [OperationFailure]`<*>` containing info about all the failed operations
     */
    fun doBatch(
        removeDependencies: Set<D> = emptySet(),
        removeRepositories: Set<R> = emptySet(),
        addDependencies: Set<D> = emptySet(),
        addRepositories: Set<R> = emptySet()
    ): List<OperationFailure<out OperationItem>>

    /**
     * List dependencies.
     *
     * @return List of dependencies.
     */
    fun listDependencies(): Collection<D>

    /**
     * Adds a single dependency atomically, then writes changes back to the backing
     * file or memory buffer.
     *
     * @return a [OperationFailure] if the operation failed, `null` otherwise
     */
    fun addDependency(dependency: D): OperationFailure<D>?

    /**
     * Removes a single dependency atomically, then writes changes back to the backing
     * file or memory buffer.
     *
     * @return a [OperationFailure] if the operation failed, `null` otherwise
     */
    fun removeDependency(dependency: D): OperationFailure<D>?

    /**
     * List repositories.
     *
     * @return List of repositories.
     */
    fun listRepositories(): Collection<R>

    /**
     * Adds a single repository atomically, then writes changes back to the backing
     * file or memory buffer.
     *
     * @return a [OperationFailure] if the operation failed, `null` otherwise
     */
    fun addRepository(repository: R): OperationFailure<R>?

    /**
     * Removes a single repository atomically, then writes changes back to the backing
     * file or memory buffer.
     *
     * @return a [OperationFailure] if the operation failed, `null` otherwise
     */
    fun removeRepository(repository: R): OperationFailure<R>?
}

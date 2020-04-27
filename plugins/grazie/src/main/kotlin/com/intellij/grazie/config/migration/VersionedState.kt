package com.intellij.grazie.config.migration

/**
 * Interface to define state with version.
 */
interface VersionedState<E : VersionedState.Version<T>, T : VersionedState<E, T>> {
  interface Version<T : VersionedState<*, T>> {
    fun migrate(state: T) = state
    fun next(): Version<T>?
  }

  /** Current version of state */
  val version: E

  /** Returns state with version incremented by 1 */
  fun increment(): T

  companion object {
    /**  Perform all available migrations starting with state [state] */
    fun <E : Version<T>, T : VersionedState<E, T>> migrate(state: T): T {
      var cur: T = state

      while (cur.version.next() != null) {
        val migrated = cur.version.migrate(cur)

        require(migrated.version == cur.version) {
          "Migration shouldn't change version, but changed from ${cur.version} to ${migrated.version}"
        }

        cur = migrated.increment()
      }
      return cur
    }
  }
}
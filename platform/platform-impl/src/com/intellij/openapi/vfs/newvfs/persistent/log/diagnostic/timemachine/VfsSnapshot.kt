// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine

import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.bind
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.fmap
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.mapCases

interface VfsSnapshot {
  val point: () -> OperationLogStorage.Iterator

  fun getFileById(fileId: Int): VirtualFileSnapshot
  fun getFileByIdIfExists(fileId: Int): VirtualFileSnapshot?

  interface VirtualFileSnapshot {
    val fileId: Int

    val nameId: Property<Int>
    val name: Property<String>
    val parentId: Property<Int>
    val parent: Property<VirtualFileSnapshot?>

    abstract class Property<T> {
      var state: State = State.UnknownYet
        protected set

      protected abstract fun compute(): State.DefinedState<T>

      @Suppress("UNCHECKED_CAST")
      fun observeState(): State.DefinedState<T> =
        when (val s = state) {
          is State.DefinedState<*> -> s as State.DefinedState<T>
          is State.UnknownYet -> synchronized(this) {
            if (state is State.UnknownYet) {
              val result = compute()
              state = result
              return result
            }
            return state as State.DefinedState<T>
          }
        }

      inline fun <R> observe(onNotAvailable: (cause: Throwable) -> R, onReady: (value: T) -> R): R =
        observeState().mapCases(onNotAvailable, onReady)

      fun get(): T = observe(onNotAvailable = { throw IllegalStateException("property expected to be Ready") }) { it }
      fun getOrNull(): T? = observe(onNotAvailable = { null }) { it }

      companion object {
        fun <T, R> Property<T>.fmap(f: (T) -> R): Property<R> = DependentPropertyFmap(this, f)
        fun <T, R> Property<T>.bind(f: (T) -> State.DefinedState<R>): Property<R> = DependentPropertyBind(this, f)

        private class DependentPropertyFmap<T, R>(private val original: Property<T>,
                                                  private val transformValue: (T) -> R) : Property<R>() {
          override fun compute(): State.DefinedState<R> {
            return original.observeState().fmap(transformValue)
          }
        }

        private class DependentPropertyBind<T, R>(private val original: Property<T>,
                                                  private val transformValue: (T) -> State.DefinedState<R>) : Property<R>() {
          override fun compute(): State.DefinedState<R> {
            return original.observeState().bind(transformValue)
          }
        }
      }

      sealed interface State {
        object UnknownYet : State

        sealed interface DefinedState<T> : State
        class NotAvailable<T>(
          // TODO: it's better to ensure a correct cause is always specified. It might be desired to know whether the cause
          //       is a lack of information or some other exception (e.g. IOException) that should treated differently.
          //       Such logic needs to be carefully adjusted along the computation paths if the need for it arises
          val cause: Throwable = UnspecifiedNotAvailableCause
        ) : DefinedState<T>
        class Ready<T>(val value: T) : DefinedState<T>

        companion object {
          fun <T> notAvailable(cause: Throwable = UnspecifiedNotAvailableCause) = NotAvailable<T>(cause)
          fun <T> ready(value: T) = Ready(value)


          inline fun <T, R> DefinedState<T>.mapCases(onNotAvailable: (cause: Throwable) -> R, onReady: (value: T) -> R) = when (this) {
            is Ready<T> -> onReady(value)
            is NotAvailable -> onNotAvailable(cause)
          }

          @Suppress("UNCHECKED_CAST")
          fun <T, R> DefinedState<T>.fmap(f: (T) -> R): DefinedState<R> = when (this) {
            is NotAvailable -> this as NotAvailable<R>
            is Ready<T> -> Ready(f(value))
          }

          @Suppress("UNCHECKED_CAST")
          fun <T, R> DefinedState<T>.bind(f: (T) -> DefinedState<R>): DefinedState<R> = when (this) {
            is NotAvailable -> this as NotAvailable<R>
            is Ready<T> -> f(value)
          }

          inline fun <T> DefinedState<T>?.orIfNotAvailable(other: () -> DefinedState<T>): DefinedState<T> = when (this) {
            is Ready -> this
            is NotAvailable -> other()
            null -> other()
          }

          abstract class GenericNotAvailableException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
          object UnspecifiedNotAvailableCause : GenericNotAvailableException("property value is not available") // TODO delete and fix usages
          /* TODO
          abstract class GenericNotAvailableCause(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
          abstract class GenericRecoveryFailureCause(message: String? = null, cause: Throwable? = null) : GenericNotAvailableCause(message, cause)
          abstract class GenericNotEnoughInformationCause(message: String? = null, cause: Throwable? = null) : GenericNotAvailableCause(message, cause)
          open class NotEnoughInformationCause(message: String = "not enough information to recover the property",
                                              cause: Throwable? = null) : GenericNotEnoughInformationCause(message, cause)
          */
        }
      }
    }
  }
}

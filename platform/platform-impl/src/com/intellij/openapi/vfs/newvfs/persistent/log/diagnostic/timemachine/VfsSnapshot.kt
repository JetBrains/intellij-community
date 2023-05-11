// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine

import com.intellij.openapi.vfs.newvfs.AttributeInputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.bind
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.fmap
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.Companion.mapCases
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State.DefinedState

interface VfsSnapshot {
  val point: () -> OperationLogStorage.Iterator

  fun getFileById(fileId: Int): VirtualFileSnapshot

  interface VirtualFileSnapshot {
    val fileId: Int

    val nameId: Property<Int>
    val parentId: Property<Int>
    val length: Property<Long>
    val timestamp: Property<Long>
    val flags: Property<@PersistentFS.Attributes Int>
    val contentRecordId: Property<Int>
    val attributesRecordId: Property<Int>

    val name: Property<String>
    val parent: Property<VirtualFileSnapshot?>

    fun getContent(): DefinedState<ByteArray>
    fun readAttribute(fileAttribute: FileAttribute): DefinedState<AttributeInputStream?>

    abstract class Property<T> {
      var state: State = State.UnknownYet
        protected set

      protected abstract fun compute(): DefinedState<T>

      override fun toString(): String = observeState().toString()

      @Suppress("UNCHECKED_CAST")
      fun observeState(): DefinedState<T> =
        when (val s = state) {
          is DefinedState<*> -> s as DefinedState<T>
          is State.UnknownYet -> synchronized(this) {
            if (state is State.UnknownYet) {
              val result = compute()
              state = result
              return result
            }
            return state as DefinedState<T>
          }
        }

      inline fun <R> observe(onNotAvailable: (cause: Throwable) -> R, onReady: (value: T) -> R): R =
        observeState().mapCases(onNotAvailable, onReady)

      fun get(): T = observe(onNotAvailable = { throw IllegalStateException("property expected to be Ready", it.cause) }) { it }
      fun getOrNull(): T? = observe(onNotAvailable = { null }) { it }

      companion object {
        fun <T, R> Property<T>.fmap(f: (T) -> R): Property<R> = DependentPropertyFmap(this, f)
        fun <T, R> Property<T>.bind(f: (T) -> DefinedState<R>): Property<R> = DependentPropertyBind(this, f)

        private class DependentPropertyFmap<T, R>(private val original: Property<T>,
                                                  private val transformValue: (T) -> R) : Property<R>() {
          override fun compute(): DefinedState<R> {
            return original.observeState().fmap(transformValue)
          }
        }

        private class DependentPropertyBind<T, R>(private val original: Property<T>,
                                                  private val transformValue: (T) -> DefinedState<R>) : Property<R>() {
          override fun compute(): DefinedState<R> {
            return original.observeState().bind(transformValue)
          }
        }
      }

      sealed interface State {
        object UnknownYet : State

        sealed interface DefinedState<out T> : State

        /**
         * Use [NotEnoughInformationCause] to designate a situation when there is not enough data to succeed the recovery
         * (though the process went normal). Throw [VfsRecoveryException] if an exception occurs during the recovery process and it is
         * considered not normal.
         */
        class NotAvailable(
          val cause: NotEnoughInformationCause
        ) : DefinedState<Nothing> {
          override fun toString(): String = "N/A ($cause)"
        }

        class Ready<T>(val value: T) : DefinedState<T> {
          override fun toString(): String = value.toString()
        }

        companion object {
          fun notAvailable(cause: NotEnoughInformationCause = UnspecifiedNotAvailableException) = NotAvailable(cause)
          fun <T> ready(value: T) = Ready(value)


          inline fun <T, R> DefinedState<T>.mapCases(onNotAvailable: (cause: Throwable) -> R, onReady: (value: T) -> R) = when (this) {
            is Ready<T> -> onReady(value)
            is NotAvailable -> onNotAvailable(cause)
          }

          inline fun <T, R> DefinedState<T>.fmap(f: (T) -> R): DefinedState<R> = when (this) {
            is NotAvailable -> this
            is Ready<T> -> Ready(f(value))
          }

          inline fun <T, R> DefinedState<T>.bind(f: (T) -> DefinedState<R>): DefinedState<R> = when (this) {
            is NotAvailable -> this
            is Ready<T> -> f(value)
          }

          inline fun <T> DefinedState<T>?.orIfNotAvailable(other: () -> DefinedState<T>): DefinedState<T> = when (this) {
            is Ready -> this
            is NotAvailable -> other()
            null -> other()
          }

          sealed class GenericNotAvailableException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
          open class NotEnoughInformationCause(message: String, cause: NotEnoughInformationCause? = null) : GenericNotAvailableException(message, cause) {
            override fun toString(): String = localizedMessage
          }
          object UnspecifiedNotAvailableException : NotEnoughInformationCause("property value is not available") // TODO delete and fix usages
          open class VfsRecoveryException(message: String? = null, cause: Throwable? = null) : GenericNotAvailableException(message, cause)
        }
      }
    }
  }
}

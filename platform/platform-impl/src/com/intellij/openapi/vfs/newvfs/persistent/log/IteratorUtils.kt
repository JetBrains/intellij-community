// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.VFileEventBasedIterator.PartialVFileEventException
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.VFileEventBasedIterator.ReadResult
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.OperationReadResult
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.OperationReadResult.Companion.getTag
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.OperationReadResult.Companion.onInvalid

object IteratorUtils {
  /**
   * [VFileEventBasedIterator] provides a more convenient API for working with VFileEvent kind of records:
   * it automatically marks up ranges of VFileEvent-related operations.
   *
   * If [VfsOperation.VFileEventOperation.EventStart] was encountered, but there is no matching [VfsOperation.VFileEventOperation.EventEnd]
   * in the storage yet, then [ReadResult.Invalid] is returned with [PartialVFileEventException] as a cause.
   *
   * Iterator should not be used after [ReadResult.Invalid] was read.
   *
   * @param logIterator must not reside inside VFileEvent-related operations range
   */
  class VFileEventBasedIterator(private val logIterator: OperationLogStorage.Iterator) : BiDiIterator<ReadResult> {
    sealed interface ReadResult {
      /**
       * @param startTag tag of the corresponding [VfsOperation.VFileEventOperation.EventStart]
       * @param begin is positioned before the [VfsOperation.VFileEventOperation.EventStart] operation
       * @param end is positioned after the [VfsOperation.VFileEventOperation.EventEnd] operation
       */
      data class VFileEventRange(val startTag: VfsOperationTag,
                                 val begin: OperationLogStorage.Iterator,
                                 val end: OperationLogStorage.Iterator) : ReadResult

      /**
       * Designates an operation that is not tied to any VFileEvent.
       * @param tag operation's tag
       * @param iter is positioned before the operation
       */
      data class SingleOperation(val tag: VfsOperationTag, val iter: OperationLogStorage.Iterator) : ReadResult

      data class Invalid(val cause: Throwable) : ReadResult
    }

    override fun hasPrevious(): Boolean = logIterator.hasPrevious()

    override fun previous(): ReadResult {
      val initIter = logIterator.copy()
      val prev = logIterator.previousFiltered(VfsOperationTagsMask.VFileEventEndMask)
        .onInvalid { return ReadResult.Invalid(it) }
      val tag = prev.getTag()
      if (tag != VfsOperationTag.VFILE_EVENT_END) {
        if (tag.isVFileEventStartOperation) {
          return ReadResult.Invalid(IllegalStateException("iterator was positioned inside VFileEvent: prev() is $tag"))
        }
        // some other operation, not inside VFileEvent
        return ReadResult.SingleOperation(tag, logIterator.copy())
      }
      // find start
      while (logIterator.hasPrevious()) {
        val rec = logIterator.previousIncomplete()
          .onInvalid { return ReadResult.Invalid(it) } as OperationReadResult.Incomplete
        if (rec.tag.isVFileEventStartOperation) {
          // found start, validate it
          if (prev is OperationReadResult.Valid) {
            val op = prev.operation as VfsOperation.VFileEventOperation.EventEnd
            if (op.eventTag != rec.tag) {
              return ReadResult.Invalid(
                IllegalStateException("the end tag doesn't match the start tag: start=${rec.tag}, end=${op.eventTag}")
              )
            }
          }
          else {
            assert(prev is OperationReadResult.Incomplete)
            // couldn't validate
          }
          return ReadResult.VFileEventRange(rec.tag, logIterator.copy(), initIter)
        }
        else if (rec.getTag() == VfsOperationTag.VFILE_EVENT_END) {
          // another end tag was encountered before the first one was matched with a starting tag
          return ReadResult.Invalid(IllegalStateException("another VFileEvent ended before the following END was matched with a START"))
        }
        else {
          // some other operation, not interesting
        }
      }
      // didn't find matching start
      return ReadResult.Invalid(PartialVFileEventException(initIter, OperationLogStorage.TraverseDirection.RETREAT))
    }

    override fun hasNext(): Boolean = logIterator.hasNext()

    override fun next(): ReadResult {
      val initIter = logIterator.copy()
      val next = logIterator.nextIncomplete()
        .onInvalid { return ReadResult.Invalid(it) } as OperationReadResult.Incomplete
      val tag = next.tag
      if (!tag.isVFileEventStartOperation) {
        if (tag == VfsOperationTag.VFILE_EVENT_END) {
          return ReadResult.Invalid(IllegalStateException("iterator was positioned inside VFileEvent: next() is VFILE_EVENT_END"))
        }
        // some other operation, not inside VFileEvent
        return ReadResult.SingleOperation(tag, initIter)
      }
      // find END
      while (logIterator.hasNext()) {
        val rec = logIterator.nextFiltered(VfsOperationTagsMask.VFileEventEndMask)
          .onInvalid { return ReadResult.Invalid(it) }
        if (rec.getTag() == VfsOperationTag.VFILE_EVENT_END) {
          // found END, validate it
          if (rec is OperationReadResult.Valid) {
            val op = rec.operation as VfsOperation.VFileEventOperation.EventEnd
            if (op.eventTag != tag) {
              return ReadResult.Invalid(
                IllegalStateException("the end tag doesn't match the start tag: start=$tag, end=${op.eventTag}"))
            }
          }
          else {
            assert(rec is OperationReadResult.Incomplete)
            // couldn't validate
          }
          return ReadResult.VFileEventRange(tag, initIter, logIterator.copy())
        }
        else if (rec.getTag().isVFileEventStartOperation) {
          // another start tag was encountered before the first one was matched with an ending tag
          return ReadResult.Invalid(
            IllegalStateException("another VFileEvent started before the first one was matched with a VFILE_EVENT_END"))
        }
        else {
          // some other operation, not interesting
        }
      }
      // didn't find matching END
      return ReadResult.Invalid(PartialVFileEventException(initIter, OperationLogStorage.TraverseDirection.ADVANCE))
    }

    /**
     * `iter.move(direction)` will produce [VfsOperation.VFileEventOperation]
     */
    data class PartialVFileEventException(val iter: OperationLogStorage.Iterator,
                                          val direction: OperationLogStorage.TraverseDirection) : Exception()
  }

  /**
   * Traverses the operations inside the range (i.e. without EventStart/EventEnd operations) in advancing manner and
   * invokes [body] on read results.
   */
  fun ReadResult.VFileEventRange.forEachContainedOperation(body: (OperationReadResult) -> Unit) {
    val start = begin.copy()
    val end = end.copy()
    start.skipNext()
    end.skipPrevious()
    while (start < end) {
      body(start.next().onInvalid {
        throw IllegalStateException("invalid record inside already processed VFileEvent content range")
      })
    }
  }

  /**
   * Never reads contents of an operation, only tags.
   * Can produce only [OperationLogStorage.OperationReadResult.Incomplete] or [OperationLogStorage.OperationReadResult.Invalid].
   */
  fun OperationLogStorage.Iterator.nextIncomplete() = nextFiltered(VfsOperationTagsMask.EMPTY)

  /**
   * @see [OperationLogStorage.Iterator.nextIncomplete]
   */
  fun OperationLogStorage.Iterator.previousIncomplete() = previousFiltered(VfsOperationTagsMask.EMPTY)

  /**
   * Skips next record efficiently, assumes that the read must succeed
   * @throws IllegalStateException in case [OperationLogStorage.OperationReadResult.Invalid] was read
   */
  fun OperationLogStorage.Iterator.skipNext() {
    nextIncomplete().onInvalid {
      throw IllegalStateException("failed to skip next record", it.cause)
    }
  }

  /**
   * Skips previous record efficiently, assumes that the read must succeed
   * @throws IllegalStateException in case [OperationLogStorage.OperationReadResult.Invalid] was read
   */
  fun OperationLogStorage.Iterator.skipPrevious() {
    previousIncomplete().onInvalid {
      throw IllegalStateException("failed to skip previous record", it.cause)
    }
  }
}
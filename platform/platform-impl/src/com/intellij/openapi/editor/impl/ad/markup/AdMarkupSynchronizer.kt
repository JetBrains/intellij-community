// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.markup

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.ad.AdTheManager.Companion.AD_DISPATCHER
import com.intellij.openapi.editor.impl.ad.markup.AdMarkupEntity.Companion.MarkupStorageAttr
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.util.Key
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asDisposable
import fleet.kernel.change
import fleet.kernel.shared
import fleet.util.AtomicRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max


@Experimental
@Service(Level.PROJECT)
internal class AdMarkupSynchronizerService(private val coroutineScope: CoroutineScope): Disposable.Default {
  fun createSynchronizer(markupEntity: AdMarkupEntity, markupModel: MarkupModelEx): CoroutineScope {
    val cs = coroutineScope.childScope("markup->entity sync", AD_DISPATCHER)
    val disposable = cs.asDisposable()
    val sync = AdMarkupSynchronizer(markupEntity, markupModel, cs)
    markupModel.document.addDocumentListener(sync, disposable)
    markupModel.addMarkupModelListener(disposable, sync)
    return cs
  }
}


@Experimental
private class AdMarkupSynchronizer(
  private val markupEntity: AdMarkupEntity,
  private val markupModel: MarkupModelEx,
  private val coroutineScope: CoroutineScope,
) : MarkupModelListener, DocumentListener {

  private val scheduledCollect = AtomicRef<Job>()
  private val id2RemovedId = TreeMap<Long, Long>()
  private val lastSeenNoveltyId = AtomicLong()

  companion object {
    private const val MAX_DEAD_MARKERS_SIZE = 10_000
    private val HIGHLIGHTER_TIMESTAMP: Key<Long> = Key.create("AD_RANGE_HIGHLIGHTER_TIMESTAMP")
    private val TIMESTAMPS = AtomicLong()
  }

  override fun beforeDocumentChange(event: DocumentEvent) {
    markupModel.processRangeHighlightersOverlappingWith(event.offset, event.offset + event.oldLength) {
      updateHighlighter(it)
      true
    }
    scheduleCollect()
  }

  override fun afterAdded(highlighter: RangeHighlighterEx) {
    markAdded(highlighter)
    scheduleCollect()
  }

  override fun afterRemoved(highlighter: RangeHighlighterEx) {
    markRemoved(highlighter)
    scheduleCollect()
  }

  override fun attributesChanged(highlighter: RangeHighlighterEx, renderersChanged: Boolean, fontStyleOrColorChanged: Boolean) {
    updateHighlighter(highlighter)
    scheduleCollect()
  }

  private fun updateHighlighter(highlighter: RangeHighlighterEx) {
    markRemoved(highlighter)
    markAdded(highlighter)
  }

  private fun markAdded(highlighter: RangeHighlighterEx) {
    highlighter.putUserData(HIGHLIGHTER_TIMESTAMP, TIMESTAMPS.incrementAndGet())
  }

  private fun markRemoved(highlighter: RangeHighlighterEx) {
    val ts = highlighter.getUserData(HIGHLIGHTER_TIMESTAMP) ?: return
    synchronized(id2RemovedId) {
      id2RemovedId[TIMESTAMPS.incrementAndGet()] = ts
      if (id2RemovedId.size > MAX_DEAD_MARKERS_SIZE) {
        id2RemovedId.remove(id2RemovedId.firstKey())
      }
    }
  }

  private fun scheduleCollect() {
    // TODO: do not cancel scheduled
    scheduledCollect.getAndSet(null)?.cancel()
    val scheduled = coroutineScope.launch {
      delay(100)
      collectBatch(lastSeenNoveltyId.get())
    }
    val alreadyScheduled = !scheduledCollect.compareAndSet(null, scheduled)
    if (alreadyScheduled) {
      scheduled.cancel()
    }
  }

  private suspend fun collectBatch(lastSeenNoveltyId: Long) {
    val (invalidateOldMarkup, toRemove) = highlightersToRemove(lastSeenNoveltyId)
    val toAdd = highlighterToAdd(invalidateOldMarkup, lastSeenNoveltyId)
    val newNoveltyId = nextNoveltyId(lastSeenNoveltyId, toAdd, toRemove)
    this.lastSeenNoveltyId.set(newNoveltyId)
    change {
      shared {
        // TODO: offsets may be outdated!!
        markupEntity[MarkupStorageAttr] = markupEntity.markupStorage.batchUpdate(
          toAdd,
          toRemove.map { it.removedId },
        )
      }
    }
  }

  private fun highlightersToRemove(lastSeenNoveltyId: Long): Pair<Boolean, List<RemovedHighlighter>> {
    return synchronized(id2RemovedId) {
      // too old, re-send all
      val invalidateOldMarkup: Boolean = (id2RemovedId.size == MAX_DEAD_MARKERS_SIZE) &&
                                         (id2RemovedId.firstKey() > lastSeenNoveltyId)
      val toRemove = if (invalidateOldMarkup) {
        emptyList()
      } else {
        id2RemovedId.tailMap(lastSeenNoveltyId, false)
          .entries
          .filter { (_, removedId) -> removedId <= lastSeenNoveltyId }
          .map { (id, removedId) -> RemovedHighlighter(id, removedId) }
      }
      (invalidateOldMarkup to toRemove)
    }
  }

  private fun highlighterToAdd(invalidateOldMarkup: Boolean, lastSeenNoveltyId: Long): List<AdRangeHighlighter> {
    val toAdd = mutableListOf<AdRangeHighlighter>()
    markupModel.processRangeHighlightersOverlappingWith(0, Int.MAX_VALUE) { highlighter ->
      val ts = highlighter.getUserData(HIGHLIGHTER_TIMESTAMP)
      if (ts != null && (invalidateOldMarkup || ts > lastSeenNoveltyId)) {
        val adHighlighter = AdRangeHighlighter.fromHighlighter(ts, highlighter)
        if (adHighlighter != null) {
          toAdd.add(adHighlighter)
        }
      }
      true
    }
    return toAdd
  }

  private fun nextNoveltyId(lastSeenNoveltyId: Long, toAdd: List<AdRangeHighlighter>, toRemove: List<RemovedHighlighter>): Long {
    val m1 = toAdd.maxOfOrNull { it.id } ?: lastSeenNoveltyId
    val m2 = toRemove.maxOfOrNull { it.id } ?: lastSeenNoveltyId
    return max(m1, m2)
  }
}

private data class RemovedHighlighter(
  val id: Long, // TODO: not used
  val removedId: Long,
) {
  override fun toString(): String = "($id, $removedId)"
}

package com.intellij.cce.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.util.EventDispatcher
import com.intellij.util.ReflectionUtil
import com.intellij.util.containers.DisposableWrapperList

class ListSizeRestriction private constructor(private val list: List<*>) {
  companion object {
    const val WAITING_INTERVAL_MS: Long = 1000
    const val MAX_ATTEMPTS_TO_WAIT_IF_SIZE_INCREASING: Long = 60

    // workaround for performance degradation during completion evaluation due to
    // application listeners are added more often than are removed
    fun applicationListeners(): ListSizeRestriction {
      val application = ApplicationManager.getApplication()
      return try {
        val dispatcher = ReflectionUtil.getField(ApplicationImpl::class.java, application,
                                                 EventDispatcher::class.java, "myDispatcher")
        val listeners = ReflectionUtil.getField(EventDispatcher::class.java, dispatcher, DisposableWrapperList::class.java, "myListeners")
        ListSizeRestriction(listeners)
      }
      catch (e: ReflectiveOperationException) {
        System.err.println("WARNING: Could not extract Application listeners. Evaluation performance may become very slow.")
        ListSizeRestriction(emptyList<Any>())
      }
    }
  }

  fun waitForSize(size: Int) {
    var currentSize = list.size
    val initialSize = currentSize
    var attemptsToWait = 0
    while (currentSize >= size && attemptsToWait < MAX_ATTEMPTS_TO_WAIT_IF_SIZE_INCREASING) {
      System.err.println("List is too large. $currentSize > $size")
      Thread.sleep(WAITING_INTERVAL_MS)
      val previousSize = currentSize
      currentSize = list.size
      if (currentSize > previousSize) {
        attemptsToWait += 1
      } else {
        attemptsToWait = 0
      }
    }

    if (initialSize != currentSize && currentSize < size) {
      System.err.println("List size decreased: $initialSize -> $currentSize")
    }
    if (currentSize >= size) {
      System.err.println("List still too large: $currentSize instead of $size")
    }
  }
}
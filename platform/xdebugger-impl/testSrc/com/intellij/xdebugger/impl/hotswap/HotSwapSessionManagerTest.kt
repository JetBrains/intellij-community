// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.HeavyPlatformTestCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class HotSwapSessionManagerTest : HeavyPlatformTestCase() {
  fun testCurrentSession() {
    val manager = HotSwapSessionManager.getInstance(project)
    val disposable = Disposer.newDisposable(testRootDisposable)
    try {
      assertNull(manager.currentSession)
      val hotSwapSession = manager.createSession(MockHotSwapProvider(), disposable)
      assertSame(hotSwapSession, manager.currentSession)
    }
    finally {
      Disposer.dispose(disposable)
    }
    assertNull(manager.currentSession)
  }

  fun testListener() {
    val manager = HotSwapSessionManager.getInstance(project)
    val disposable = Disposer.newDisposable(testRootDisposable)
    try {
      val list = addStatusListener(disposable)
      assertEmpty(list)
      manager.createSession(MockHotSwapProvider(), disposable)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  fun testListenerAfterSessionStart() {
    val manager = HotSwapSessionManager.getInstance(project)
    val list = mutableListOf<HotSwapVisibleStatus?>()
    val disposable1 = Disposer.newDisposable(testRootDisposable)
    val disposable2 = Disposer.newDisposable(testRootDisposable)
    try {
      manager.createSession(MockHotSwapProvider(), disposable1)
      manager.addListener(createStatusListener(list), disposable2)
      assertEquals(HotSwapVisibleStatus.NO_CHANGES, list.single())
    }
    finally {
      Disposer.dispose(disposable1)
      try {
        assertEquals(2, list.size)
        assertEquals(HotSwapVisibleStatus.SESSION_COMPLETED, list.last())
      }
      finally {
        Disposer.dispose(disposable2)
      }
    }
  }

  fun testOnChanges() {
    val manager = HotSwapSessionManager.getInstance(project)
    val disposable = Disposer.newDisposable(testRootDisposable)
    try {
      val list = addStatusListener(disposable)
      assertEmpty(list)
      val provider = MockHotSwapProvider()
      val hotSwapSession = manager.createSession(provider, disposable)
      assertEmpty(hotSwapSession.getChanges())
      assertEmpty(list)

      provider.collector.addFile(MockVirtualFile("a.txt"))
      assertEquals(1, list.size)
      assertEquals(HotSwapVisibleStatus.CHANGES_READY, list.last())
      assertEquals(1, hotSwapSession.getChanges().size)

      provider.collector.addFile(MockVirtualFile("b.txt"))
      assertEquals(2, list.size)
      assertEquals(HotSwapVisibleStatus.CHANGES_READY, list.last())
      assertEquals(2, hotSwapSession.getChanges().size)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  fun testHotSwap() {
    val manager = HotSwapSessionManager.getInstance(project)
    val disposable = Disposer.newDisposable(testRootDisposable)
    try {
      val list = addStatusListener(disposable)
      assertEmpty(list)
      val provider = MockHotSwapProvider()
      val hotSwapSession = manager.createSession(provider, disposable)

      run {
        val start = 0
        provider.collector.addFile(MockVirtualFile("a.txt"))
        assertEquals(start + 1, list.size)
        assertEquals(HotSwapVisibleStatus.CHANGES_READY, list.last())
        val listener = provider.performHotSwap(hotSwapSession)
        assertEquals(start + 2, list.size)
        assertEquals(HotSwapVisibleStatus.IN_PROGRESS, list.last())
        listener.onFinish()
        assertEquals(start + 3, list.size)
        assertEquals(HotSwapVisibleStatus.NO_CHANGES, list.last())
        assertEmpty(hotSwapSession.getChanges())
      }

      run {
        val start = 3
        provider.collector.addFile(MockVirtualFile("a.txt"))
        assertEquals(start + 1, list.size)
        assertEquals(HotSwapVisibleStatus.CHANGES_READY, list.last())
        val listener = provider.performHotSwap(hotSwapSession)
        assertEquals(start + 2, list.size)
        assertEquals(HotSwapVisibleStatus.IN_PROGRESS, list.last())
        listener.onSuccessfulReload()
        assertEquals(start + 3, list.size)
        assertEquals(HotSwapVisibleStatus.NO_CHANGES, list.last())
        assertEmpty(hotSwapSession.getChanges())
      }

      run {
        val start = 6
        provider.collector.addFile(MockVirtualFile("a.txt"))
        assertEquals(start + 1, list.size)
        assertEquals(HotSwapVisibleStatus.CHANGES_READY, list.last())
        val listener = provider.performHotSwap(hotSwapSession)
        assertEquals(start + 2, list.size)
        assertEquals(HotSwapVisibleStatus.IN_PROGRESS, list.last())
        listener.onCanceled()
        assertEquals(start + 3, list.size)
        assertEquals(HotSwapVisibleStatus.CHANGES_READY, list.last())
        assertEquals(1, hotSwapSession.getChanges().size)
      }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  fun testSameHotSwapStatusHasNoEffect() {
    val manager = HotSwapSessionManager.getInstance(project)
    val disposable0 = Disposer.newDisposable(testRootDisposable)
    val disposable1 = Disposer.newDisposable(testRootDisposable)
    val list = addStatusListener(disposable0)

    val provider = MockHotSwapProvider()
    val hotSwapSession = manager.createSession(provider, disposable1)

    provider.collector.addFile(MockVirtualFile("a.txt"))
    assertEquals(HotSwapVisibleStatus.CHANGES_READY, list.single())

    val listener = hotSwapSession.startHotSwapListening()
    assertEquals(2, list.size)
    assertEquals(HotSwapVisibleStatus.IN_PROGRESS, list.last())

    listener.onCanceled()
    assertEquals(3, list.size)
    assertEquals(HotSwapVisibleStatus.CHANGES_READY, list.last())

    listener.onCanceled()
    assertEquals(3, list.size)

    Disposer.dispose(disposable1)
    assertEquals(4, list.size)
    assertEquals(HotSwapVisibleStatus.SESSION_COMPLETED, list.last())

    listener.onCanceled()
    assertEquals(4, list.size)
  }

  fun testAddingListenerConcurrentlyToChanges() {
    repeat(100) {
      runBlocking {
        val manager = HotSwapSessionManager.getInstance(project)
        val list = mutableListOf<HotSwapVisibleStatus?>()
        val disposable = Disposer.newDisposable(testRootDisposable)
        try {
          val addFile = launch(Dispatchers.Default) {
            val provider = MockHotSwapProvider()
            manager.createSession(provider, disposable)
            provider.collector.addFile(MockVirtualFile("a.txt"))
          }
          val addListener = launch(Dispatchers.Default) {
            manager.addListener(createStatusListener(list), disposable)
          }
          addFile.join()
          addListener.join()
          assertEquals(HotSwapVisibleStatus.CHANGES_READY, list.last())
        }
        finally {
          Disposer.dispose(disposable)
        }
      }
    }
  }

  fun testAddingListenerConcurrentlyToSessionClose() {
    repeat(100) {
      runBlocking {
        val manager = HotSwapSessionManager.getInstance(project)
        val list = mutableListOf<HotSwapVisibleStatus?>()
        val disposable1 = Disposer.newDisposable(testRootDisposable)
        val disposable2 = Disposer.newDisposable(testRootDisposable)
        try {
          manager.createSession(MockHotSwapProvider(), disposable1)
          val closeOldAndAddFile = launch(Dispatchers.Default) {
            Disposer.dispose(disposable1)
            val provider = MockHotSwapProvider()
            manager.createSession(provider, disposable2)
            provider.collector.addFile(MockVirtualFile("a.txt"))
          }
          val addListener = launch(Dispatchers.Default) {
            manager.addListener(createStatusListener(list), disposable2)
          }
          closeOldAndAddFile.join()
          addListener.join()
          assertEquals(HotSwapVisibleStatus.CHANGES_READY, list.last())
        }
        finally {
          Disposer.dispose(disposable2)
        }
      }
    }
  }

  fun testMultipleSessionsNotifyLastOneWhenNoSelected() {
    val disposable0 = Disposer.newDisposable(testRootDisposable)
    val disposable1 = Disposer.newDisposable(testRootDisposable)
    val disposable2 = Disposer.newDisposable(testRootDisposable)

    val manager = HotSwapSessionManager.getInstance(project)
    val provider1 = MockHotSwapProvider()
    val session1 = manager.createSession(provider1, disposable1)
    val provider2 = MockHotSwapProvider()
    val session2 = manager.createSession(provider2, disposable2)

    val list = addSessionAndStatusListener(disposable0)

    assertSame(session2, manager.currentSession)
    assertEquals(session2 to HotSwapVisibleStatus.NO_CHANGES, list.single())

    provider1.collector.addFile(MockVirtualFile("a.txt"))
    assertEquals(session2 to HotSwapVisibleStatus.NO_CHANGES, list.single())

    provider2.collector.addFile(MockVirtualFile("a.txt"))
    assertEquals(2, list.size)
    assertEquals(session2 to HotSwapVisibleStatus.CHANGES_READY, list.last())

    Disposer.dispose(disposable2)
    assertEquals(4, list.size)
    assertEquals(session2 to HotSwapVisibleStatus.SESSION_COMPLETED, list[list.size - 2])
    assertEquals(session1 to HotSwapVisibleStatus.CHANGES_READY, list.last())

    Disposer.dispose(disposable1)
    assertEquals(5, list.size)
    assertEquals(session1 to HotSwapVisibleStatus.SESSION_COMPLETED, list.last())

    Disposer.dispose(disposable0)
    assertEquals(5, list.size)
  }

  fun testCloseNonSelectedSession() {
    val disposable0 = Disposer.newDisposable(testRootDisposable)
    val disposable1 = Disposer.newDisposable(testRootDisposable)
    val disposable2 = Disposer.newDisposable(testRootDisposable)

    val manager = HotSwapSessionManager.getInstance(project)
    val provider1 = MockHotSwapProvider()
    manager.createSession(provider1, disposable1)
    val provider2 = MockHotSwapProvider()
    val session2 = manager.createSession(provider2, disposable2)

    val list = addSessionAndStatusListener(disposable0)

    assertSame(session2, manager.currentSession)
    assertEquals(session2 to HotSwapVisibleStatus.NO_CHANGES, list.single())

    provider1.collector.addFile(MockVirtualFile("a.txt"))
    provider2.collector.addFile(MockVirtualFile("a.txt"))

    assertEquals(2, list.size)
    Disposer.dispose(disposable1)
    assertEquals(2, list.size)

    Disposer.dispose(disposable2)
    assertEquals(3, list.size)
    assertEquals(session2 to HotSwapVisibleStatus.SESSION_COMPLETED, list.last())

    Disposer.dispose(disposable0)
    assertEquals(3, list.size)
  }

  fun testSessionSelection() {
    val disposable0 = Disposer.newDisposable(testRootDisposable)
    val disposable1 = Disposer.newDisposable(testRootDisposable)
    val disposable2 = Disposer.newDisposable(testRootDisposable)

    val manager = HotSwapSessionManager.getInstance(project)
    val provider1 = MockHotSwapProvider()
    val session1 = manager.createSession(provider1, disposable1)
    val provider2 = MockHotSwapProvider()
    val session2 = manager.createSession(provider2, disposable2)

    val list = addSessionAndStatusListener(disposable0)

    assertSame(session2, manager.currentSession)
    assertEquals(session2 to HotSwapVisibleStatus.NO_CHANGES, list.single())

    manager.onSessionSelected(session1)
    assertSame(session1, manager.currentSession)
    assertEquals(2, list.size)
    assertEquals(session1 to HotSwapVisibleStatus.NO_CHANGES, list.last())

    provider1.collector.addFile(MockVirtualFile("a.txt"))
    assertEquals(3, list.size)
    assertEquals(session1 to HotSwapVisibleStatus.CHANGES_READY, list.last())

    manager.onSessionSelected(session2)
    assertSame(session2, manager.currentSession)
    assertEquals(4, list.size)
    assertEquals(session2 to HotSwapVisibleStatus.NO_CHANGES, list.last())

    provider2.collector.addFile(MockVirtualFile("a.txt"))
    assertEquals(5, list.size)
    assertEquals(session2 to HotSwapVisibleStatus.CHANGES_READY, list.last())

    Disposer.dispose(disposable2)
    assertEquals(7, list.size)
    assertEquals(session2 to HotSwapVisibleStatus.SESSION_COMPLETED, list[list.size - 2])
    assertEquals(session1 to HotSwapVisibleStatus.CHANGES_READY, list.last())

    Disposer.dispose(disposable1)
    assertEquals(8, list.size)
    assertEquals(session1 to HotSwapVisibleStatus.SESSION_COMPLETED, list.last())

    Disposer.dispose(disposable0)
    assertEquals(8, list.size)
  }

  fun testSelectionAlreadySelectedOrClosedHasNoEffect() {
    val disposable0 = Disposer.newDisposable(testRootDisposable)
    val disposable1 = Disposer.newDisposable(testRootDisposable)

    val manager = HotSwapSessionManager.getInstance(project)
    val provider1 = MockHotSwapProvider()
    val session1 = manager.createSession(provider1, disposable1)

    val list = addStatusListener(disposable0)

    assertSame(session1, manager.currentSession)
    assertEquals(HotSwapVisibleStatus.NO_CHANGES, list.single())

    manager.onSessionSelected(session1)
    assertSame(session1, manager.currentSession)
    assertEquals(HotSwapVisibleStatus.NO_CHANGES, list.last())

    manager.onSessionSelected(session1)
    assertSame(session1, manager.currentSession)
    assertEquals(HotSwapVisibleStatus.NO_CHANGES, list.last())

    Disposer.dispose(disposable1)
    assertNull(manager.currentSession)
    assertEquals(2, list.size)
    assertEquals(HotSwapVisibleStatus.SESSION_COMPLETED, list.last())

    manager.onSessionSelected(session1)
    assertNull(manager.currentSession)
    assertEquals(2, list.size)
    assertEquals(HotSwapVisibleStatus.SESSION_COMPLETED, list.last())

    Disposer.dispose(disposable0)
  }

  private fun addStatusListener(disposable: Disposable): List<HotSwapVisibleStatus?> {
    val list = mutableListOf<HotSwapVisibleStatus?>()
    HotSwapSessionManager.getInstance(project).addListener(createStatusListener(list), disposable)
    return list
  }

  private fun createStatusListener(list: MutableList<HotSwapVisibleStatus?>) = HotSwapChangesListener {
    synchronized(list) {
      val session = HotSwapSessionManager.getInstance(project).currentSession
      list.add(session?.currentStatus)
    }
  }

  private fun addSessionAndStatusListener(disposable: Disposable): List<Pair<HotSwapSession<*>?, HotSwapVisibleStatus?>> {
    val list = mutableListOf<Pair<HotSwapSession<*>?, HotSwapVisibleStatus?>>()
    val manager = HotSwapSessionManager.getInstance(project)
    manager.addListener(HotSwapChangesListener {
      synchronized<Unit>(list) {
        val session = manager.currentSession
        list.add(session to session?.currentStatus)
      }
    }, disposable)
    return list
  }

}

internal class MockHotSwapProvider : HotSwapProvider<MockVirtualFile> {
  lateinit var collector: MockChangesCollector

  override fun createChangesCollector(
    session: HotSwapSession<MockVirtualFile>,
    coroutineScope: CoroutineScope,
    listener: SourceFileChangesListener,
  ): SourceFileChangesCollector<MockVirtualFile> = MockChangesCollector(listener).also { collector = it }

  fun performHotSwap(session: HotSwapSession<MockVirtualFile>) = session.startHotSwapListening()
  override fun performHotSwap(context: DataContext, session: HotSwapSession<MockVirtualFile>) {
    error("Not supported")
  }
}

internal class MockChangesCollector(private val listener: SourceFileChangesListener) : SourceFileChangesCollector<MockVirtualFile> {
  private val files = mutableSetOf<MockVirtualFile>()
  fun addFile(file: MockVirtualFile) {
    files.add(file)
    listener.onNewChanges()
  }

  override fun getChanges(): Set<MockVirtualFile> = files

  override fun resetChanges() {
    files.clear()
  }

  override fun dispose() {
  }
}

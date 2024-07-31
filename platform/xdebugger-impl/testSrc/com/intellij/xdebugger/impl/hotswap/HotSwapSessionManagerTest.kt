// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.mock.MockVirtualFile
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
      val list = mutableListOf<HotSwapVisibleStatus>()
      manager.addListener(HotSwapChangesListener { _, status ->
        list.add(status)
      }, disposable)
      assertEmpty(list)
      manager.createSession(MockHotSwapProvider(), disposable)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  fun testListenerAfterSessionStart() {
    val manager = HotSwapSessionManager.getInstance(project)
    val list = mutableListOf<HotSwapVisibleStatus>()
    val disposable1 = Disposer.newDisposable(testRootDisposable)
    val disposable2 = Disposer.newDisposable(testRootDisposable)
    try {
      manager.createSession(MockHotSwapProvider(), disposable1)
      manager.addListener(HotSwapChangesListener { _, status ->
        list.add(status)
      }, disposable2)
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

  fun testOnChanges(): Unit = runBlocking {
    val manager = HotSwapSessionManager.getInstance(project)
    val disposable = Disposer.newDisposable(testRootDisposable)
    try {
      val list = mutableListOf<HotSwapVisibleStatus>()
      manager.addListener(HotSwapChangesListener { _, status ->
        list.add(status)
      }, disposable)
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
      assertEquals(1, list.size)
      assertEquals(2, hotSwapSession.getChanges().size)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  fun testHotSwap(): Unit = runBlocking {
    val manager = HotSwapSessionManager.getInstance(project)
    val disposable = Disposer.newDisposable(testRootDisposable)
    try {
      val list = mutableListOf<HotSwapVisibleStatus>()
      manager.addListener(HotSwapChangesListener { _, status ->
        list.add(status)
      }, disposable)
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

  fun testAddingListenerConcurrentlyToChanges() {
    repeat(100) {
      runBlocking {
        val manager = HotSwapSessionManager.getInstance(project)
        val list = mutableListOf<HotSwapVisibleStatus>()
        val disposable = Disposer.newDisposable(testRootDisposable)
        try {
          val addFile = launch(Dispatchers.Default) {
            val provider = MockHotSwapProvider()
            manager.createSession(provider, disposable)
            provider.collector.addFile(MockVirtualFile("a.txt"))
          }
          val addListener = launch(Dispatchers.Default) {
            manager.addListener(HotSwapChangesListener { _, status ->
              synchronized(list) {
                list.add(status)
              }
            }, disposable)
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
        val list = mutableListOf<HotSwapVisibleStatus>()
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
            manager.addListener(HotSwapChangesListener { _, status ->
              synchronized(list) {
                list.add(status)
              }
            }, disposable2)
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
}

internal class MockHotSwapProvider : HotSwapProvider<MockVirtualFile> {
  lateinit var collector: MockChangesCollector

  override fun createChangesCollector(
    session: HotSwapSession<MockVirtualFile>,
    coroutineScope: CoroutineScope,
    listener: SourceFileChangesListener<MockVirtualFile>,
  ): SourceFileChangesCollector<MockVirtualFile> = MockChangesCollector(listener).also { collector = it }

  fun performHotSwap(session: HotSwapSession<MockVirtualFile>) = session.startHotSwapListening()
  override fun performHotSwap(context: DataContext, session: HotSwapSession<MockVirtualFile>) {
    error("Not supported")
  }
}

internal class MockChangesCollector(private val listener: SourceFileChangesListener<MockVirtualFile>) : SourceFileChangesCollector<MockVirtualFile> {
  private val files = mutableSetOf<MockVirtualFile>()
  fun addFile(file: MockVirtualFile) {
    files.add(file)
    listener.onFileChange(file)
  }

  override fun getChanges(): Set<MockVirtualFile> = files

  override fun resetChanges() {
    files.clear()
  }

  override fun dispose() {
  }
}

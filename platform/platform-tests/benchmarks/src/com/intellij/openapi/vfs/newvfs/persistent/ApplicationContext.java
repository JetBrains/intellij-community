// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.testFramework.fixtures.BareTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * State object for use in JMH-benchmarks: initializes BareTestFixture, which, in turn,
 * initializes 'application'
 * //FIXME RC: tearDown method doesn't stop EDT, hence JMH can't terminate forked JVM.
 */
@State(Scope.Benchmark)
public class ApplicationContext {
  public BareTestFixture fixture;

  //RC: initialization/shutdown code mostly copied from TestFixtureRule

  @Setup
  public void setup() throws Exception {
    ApplicationManagerEx.setInStressTest(true);
    fixture = IdeaTestFixtureFactory.getFixtureFactory().createBareFixture();
    fixture.setUp();
  }

  @TearDown
  public void tearDown() throws Exception {
    fixture.tearDown();

    IdeEventQueue.applicationClose();
    ShutDownTracker.getInstance().run();

    fixture = null;


    AppExecutorUtil.shutdownApplicationScheduledExecutorService();
    //FIXME RC: EDT is not stopped, hence JMH can't terminate forked JVM.
    final Thread[] threads = new Thread[1024];
    final int threadCount = Thread.enumerate(threads);
    for (int i = 0; i < threadCount; i++) {
      Thread thread = threads[i];
      final String threadName = thread.getName();
      if (thread != Thread.currentThread()
          && (threadName.contains("AWT")
              || threadName.contains("I/O pool"))) {
        thread.interrupt();
      }
    }
  }
}

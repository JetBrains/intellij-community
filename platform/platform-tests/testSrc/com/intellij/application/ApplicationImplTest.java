/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.application;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.Timings;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class ApplicationImplTest extends PlatformTestCase {
  @Override
  protected boolean isRunInWriteAction() {
    return false;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    exception = null;
  }

  private volatile Throwable exception;
  public void testAcquireReadActionLockVsRunReadActionPerformance() throws Throwable {
    final int N = 100000000;
    final Application application = ApplicationManager.getApplication();
    String err = null;

    for (int i=0; i<4; i++) {
      Callable<String> runnable = new Callable<String>() {
        @Override
        public String call() throws Exception {
          try {
            assertFalse(application.isReadAccessAllowed());
            long l2 = PlatformTestUtil.measure(new Runnable() {
              @Override
              public void run() {
                for (int i = 0; i < N; i++) {
                  AccessToken token = application.acquireReadActionLock();
                  try {
                    // do it
                  }
                  finally {
                    token.finish();
                  }
                }
              }
            });

            long l1 = PlatformTestUtil.measure(new Runnable() {
              @Override
              public void run() {
                for (int i=0; i<N; i++) {
                  application.runReadAction(new Runnable() {
                    @Override
                    public void run() {
                    }
                  });
                }
              }
            });

            assertFalse(application.isReadAccessAllowed());
            int ratioPercent = (int)((l1 - l2) * 100.0 / l1);
            if (Math.abs(ratioPercent) > 20) {
              return "Suspiciously different times for acquireReadActionLock(" +l2 + "ms) vs runReadAction(" + l1 + "ms). Ratio: "+ ratioPercent + "%";
            }
          }
          catch (Throwable e) {
            exception = e;
          }
          return null;
        }
      };

      err = application.executeOnPooledThread(runnable).get();
      if (err == null) break;
      System.err.println("Still trying, attempt "+i+": "+err);
    }

    assertNull(err);
    if (exception != null) throw exception;
  }


  public void testLockPerformance() throws InterruptedException {
    int iterations = Timings.adjustAccordingToMySpeed(3000, true);
    System.out.println("iterations = " + iterations);
    final int readIterations = iterations;
    final int writeIterations = iterations;

    final int numOfThreads = 10;
    PlatformTestUtil.startPerformanceTest("lock performance", 200, new ThrowableRunnable() {
      @Override
      public void run() throws Throwable {
        final CountDownLatch reads = new CountDownLatch(numOfThreads);
        for (int i = 0; i < numOfThreads; i++) {
          final String name = "stress thread " + i;
          new Thread(new Runnable() {
            @Override
            public void run() {
              System.out.println(name);
              for (int i = 0; i < readIterations; i++) {
                ApplicationManager.getApplication().runReadAction(new Runnable() {
                  @Override
                  public void run() {

                  }
                });
              }

              reads.countDown();
            }
          }, name).start();
        }

        System.out.println("write start");
        for (int i = 0; i < writeIterations; i++) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
            }
          });
        }
        System.out.println("write end");
        reads.await();
      }
    }).assertTiming();
  }

  private volatile boolean tryingToStartWriteAction;
  private volatile boolean readStarted;
  public void testReadWontStartWhenWriteIsPending() throws Throwable {
    int N = 5;
    final AtomicBoolean[] anotherThreadStarted = new AtomicBoolean[N];
    final AtomicBoolean[] anotherReadActionStarted = new AtomicBoolean[N];
    for (int i = 0; i < anotherReadActionStarted.length; i++) {
      anotherReadActionStarted[i] = new AtomicBoolean();
      anotherThreadStarted[i] = new AtomicBoolean();
    }
    final StringBuffer LOG = new StringBuffer();
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
              LOG.append("inside read action\n");
              readStarted = true;
              while (!tryingToStartWriteAction);
              TimeoutUtil.sleep(100);

              for (int i = 0; i < anotherReadActionStarted.length; i++) {
                final int finalI = i;
                new Thread(new Runnable() {
                  @Override
                  public void run() {
                    LOG.append("\nanother thread started " + finalI);
                    anotherThreadStarted[finalI].set(true);
                    ApplicationManager.getApplication().runReadAction(new Runnable() {
                      @Override
                      public void run() {
                        LOG.append("\ninside another thread read action " + finalI);
                        anotherReadActionStarted[finalI].set(true);
                        try {
                          Thread.sleep(100);
                        }
                        catch (InterruptedException e) {
                          throw new RuntimeException(e);
                        }
                        anotherReadActionStarted[finalI].set(false);
                        LOG.append("\nfinished another thread read action " + finalI);
                      }
                    });
                    LOG.append("\nanother thread finished " + finalI);
                  }
                },"another read action "+i).start();
              }

              for (AtomicBoolean threadStarted : anotherThreadStarted) {
                while (!threadStarted.get()) ;
              }
              // now the other threads try to get read lock. we should not let them
              for (int i=0; i<10; i++) {
                for (AtomicBoolean readStarted : anotherReadActionStarted) {
                  assertThat(!readStarted.get(), "must not start another read action while write is pending");
                }
                TimeoutUtil.sleep(20);
              }
              LOG.append("\nfinished read action");
            }
          });
        }
        catch (Throwable e) {
          exception = e;
        }
      }
    }, "read").start();


    while (!readStarted);
    tryingToStartWriteAction = true;
    LOG.append("\nwrite about to start");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        LOG.append("\ninside write action");
        for (AtomicBoolean readStarted : anotherReadActionStarted) {
          assertThat(!readStarted.get(), "must not start another read action while write is running");
        }
        LOG.append("\nfinished write action");
      }
    });
    if (exception != null) {
      System.err.println(LOG);
      throw exception;
    }
  }

  private void assertThat(boolean condition, String msg) {
    if (!condition) {
      exception = new Exception(msg);
      throw new RuntimeException(msg);
    }
  }

  public void testProgressVsReadAction() throws Throwable {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new ThrowableComputable<Void, Exception>() {
      @Override
      public Void compute() throws Exception {
        try {
          assertFalse(ApplicationManager.getApplication().isReadAccessAllowed());
          assertFalse(ApplicationManager.getApplication().isDispatchThread());
          for (int i=0; i<100;i++) {
            SwingUtilities.invokeLater(new Runnable() {
              @Override
              public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                  @Override
                  public void run() {
                    TimeoutUtil.sleep(20);
                  }
                });
              }
            });
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              @Override
              public void run() {
                TimeoutUtil.sleep(20);
              }
            });
          }
        }
        catch (Exception e) {
          exception = e;
        }
        return null;
      }
    }, "cc", false, getProject());
    if (exception != null) throw exception;
  }

  public void testAsyncProgressVsReadAction() throws Throwable {
    Future<?> future = ((ProgressManagerImpl)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(
      new Task.Backgroundable(getProject(), "xx") {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            assertFalse(ApplicationManager.getApplication().isReadAccessAllowed());
            assertFalse(ApplicationManager.getApplication().isDispatchThread());
          }
          catch (Exception e) {
            exception = e;
          }
        }
      });
    future.get();
    if (exception != null) throw exception;
  }

  public void testWriteActionIsAllowedFromEDTOnly() throws InterruptedException {
    Thread thread = new Thread("test") {
      @Override
      public void run() {
        try {
          ApplicationManager.getApplication().runWriteAction(EmptyRunnable.getInstance());
        }
        catch (Throwable e) {
          exception = e;
        }
      }
    };
    thread.start();
    thread.join();
    assertNotNull(exception);
  }

  public void testRunProcessWithProgressSynchronouslyInReadAction() throws Throwable {
    boolean result = ((ApplicationEx)ApplicationManager.getApplication())
      .runProcessWithProgressSynchronouslyInReadAction(getProject(), "title", true, "cancel", null, new Runnable() {
        @Override
        public void run() {
          try {
            assertFalse(SwingUtilities.isEventDispatchThread());
            assertTrue(ApplicationManager.getApplication().isReadAccessAllowed());
          }
          catch (Throwable e) {
            exception = e;
          }
        }
      });
    assertTrue(result);
    if (exception != null) throw exception;
  }

  public void testRunProcessWithProgressSynchronouslyInReadActionWithPendingWriteAction() throws Throwable {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(EmptyRunnable.getInstance());
      }
    });
    boolean result = ((ApplicationEx)ApplicationManager.getApplication())
      .runProcessWithProgressSynchronouslyInReadAction(getProject(), "title", true, "cancel", null, new Runnable() {
        @Override
        public void run() {
          TimeoutUtil.sleep(10000);
        }
      });
    assertTrue(result);
    UIUtil.dispatchAllInvocationEvents();
    if (exception != null) throw exception;
  }
}

/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.util.messages;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.messages.impl.MessageBusImpl;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class MessageBusTest extends TestCase {
  private MessageBus myBus;
  private List<String> myLog;

  public interface T1Listener {
    void t11();
    void t12();
  }

  public interface T2Listener {
    void t21();
    void t22();
  }

  private static final Topic<T1Listener> TOPIC1 = new Topic<T1Listener>("T1", T1Listener.class);
  private static final Topic<T2Listener> TOPIC2 = new Topic<T2Listener>("T2", T2Listener.class);

  private class T1Handler implements T1Listener {
    private final String id;

    public T1Handler(final String id) {
      this.id = id;
    }

    @Override
    public void t11() {
      myLog.add(id + ":" + "t11");
    }

    @Override
    public void t12() {
      myLog.add(id + ":" + "t12");
    }
  }
  private class T2Handler implements T2Listener {
    private final String id;

    public T2Handler(final String id) {
      this.id = id;
    }

    @Override
    public void t21() {
      myLog.add(id + ":" + "t21");
    }

    @Override
    public void t22() {
      myLog.add(id + ":" + "t22");
    }
  }


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myBus = MessageBusFactory.newMessageBus(this);
    myLog = new ArrayList<String>();
  }

  public void testNoListenersSubscribed() {
    myBus.syncPublisher(TOPIC1).t11();
    assertEvents();
  }

  public void testSingleMessage() {
    final MessageBusConnection connection = myBus.connect();
    connection.subscribe(TOPIC1, new T1Handler("c"));
    myBus.syncPublisher(TOPIC1).t11();
    assertEvents("c:t11");
  }

  public void testSingleMessageToTwoConnections() {
    final MessageBusConnection c1 = myBus.connect();
    c1.subscribe(TOPIC1, new T1Handler("c1"));

    final MessageBusConnection c2 = myBus.connect();
    c2.subscribe(TOPIC1, new T1Handler("c2"));

    myBus.syncPublisher(TOPIC1).t11();
    assertEvents("c1:t11", "c2:t11");
  }

  public void testTwoMessagesWithSingleSubscription() {
    final MessageBusConnection connection = myBus.connect();
    connection.subscribe(TOPIC1, new T1Handler("c"));
    myBus.syncPublisher(TOPIC1).t11();
    myBus.syncPublisher(TOPIC1).t12();

    assertEvents("c:t11", "c:t12");
  }

  public void testTwoMessagesWithDoubleSubscription() {
    final MessageBusConnection c1 = myBus.connect();
    c1.subscribe(TOPIC1, new T1Handler("c1"));

    final MessageBusConnection c2 = myBus.connect();
    c2.subscribe(TOPIC1, new T1Handler("c2"));

    myBus.syncPublisher(TOPIC1).t11();
    myBus.syncPublisher(TOPIC1).t12();

    assertEvents("c1:t11", "c2:t11", "c1:t12", "c2:t12");
  }

  public void testEventFiresAnotherEvent() {
    myBus.connect().subscribe(TOPIC1, new T1Listener() {
      @Override
      public void t11() {
        myLog.add("inside:t11");
        myBus.syncPublisher(TOPIC2).t21();
        myLog.add("inside:t11:done");
      }

      @Override
      public void t12() {
        myLog.add("c1:t12");
      }
    });

    final MessageBusConnection conn = myBus.connect();
    conn.subscribe(TOPIC1, new T1Handler("handler1"));
    conn.subscribe(TOPIC2, new T2Handler("handler2"));

    myBus.syncPublisher(TOPIC1).t12();
    assertEvents("c1:t12", "handler1:t12");

    myBus.syncPublisher(TOPIC1).t11();
    assertEvents("c1:t12\n" +
                 "handler1:t12\n" +
                 "inside:t11\n" +
                 "handler1:t11\n" +
                 "handler2:t21\n" +
                 "inside:t11:done");
  }

  public void testConnectionTerminatedInDispatch() {
    final MessageBusConnection conn1 = myBus.connect();
    conn1.subscribe(TOPIC1, new T1Listener() {
      @Override
      public void t11() {
        conn1.disconnect();
        myLog.add("inside:t11");
        myBus.syncPublisher(TOPIC2).t21();
        myLog.add("inside:t11:done");
      }

      @Override
      public void t12() {
        myLog.add("inside:t12");
      }
    });
    conn1.subscribe(TOPIC2, new T2Handler("C1T2Handler"));

    final MessageBusConnection conn2 = myBus.connect();
    conn2.subscribe(TOPIC1, new T1Handler("C2T1Handler"));
    conn2.subscribe(TOPIC2, new T2Handler("C2T2Handler"));

    myBus.syncPublisher(TOPIC1).t11();
    assertEvents("inside:t11",
                 "C2T1Handler:t11",
                 "C2T2Handler:t21",
                 "inside:t11:done");
    myBus.syncPublisher(TOPIC1).t12();

    assertEvents("inside:t11",
                 "C2T1Handler:t11",
                 "C2T2Handler:t21",
                 "inside:t11:done",
                 "C2T1Handler:t12");
  }

  public void testPostingPerformanceWithLowListenerDensityInHierarchy() {
    //simulating million fileWithNoDocumentChanged events on refresh in a thousand-module project
    MessageBusImpl childBus = new MessageBusImpl(this, myBus);
    childBus.connect().subscribe(TOPIC1, new T1Listener() {
      @Override
      public void t11() {
      }

      @Override
      public void t12() {
      }
    });
    for (int i = 0; i < 1000; i++) {
      new MessageBusImpl(this, childBus);
    }

    PlatformTestUtil.assertTiming("Too long", 3000, new Runnable() {
      @Override
      public void run() {
        T1Listener publisher = myBus.syncPublisher(TOPIC1);
        for (int i = 0; i < 1000000; i++) {
          publisher.t11();
        }
      }
    });
  }

  public void testStress() throws Throwable {
    final int threadsNumber = 10;
    final int iterationsNumber = 100;
    final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
    final CountDownLatch latch = new CountDownLatch(threadsNumber);
    final MessageBus parentBus = MessageBusFactory.newMessageBus("parent");
    for (int i = 0; i < threadsNumber; i++) {
      new Thread(String.valueOf(i)) {
        @Override
        public void run() {
          int remains = iterationsNumber;
          try {
            while (remains-- > 0) {
              //noinspection ThrowableResultOfMethodCallIgnored
              if (exception.get() != null) {
                break;
              }
              new MessageBusImpl(String.format("child-%s-%s", Thread.currentThread().getName(), remains), parentBus);
            }
          }
          catch (Throwable e) {
            exception.set(e);
          }
          finally {
            latch.countDown();
          }
        }
      }.start();
    }
    latch.await();
    final Throwable e = exception.get();
    if (e != null) {
      throw e;
    }
  }


  private void assertEvents(String... expected) {
    String joinExpected = StringUtil.join(expected, "\n");
    String joinActual = StringUtil.join(myLog, "\n");

    assertEquals("events mismatch", joinExpected, joinActual);
  }
}

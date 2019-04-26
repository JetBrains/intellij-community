package com.intellij.util;

import com.intellij.testFramework.LightPlatformTestCase;

import java.util.EventListener;

public class EventDispatcherTest extends LightPlatformTestCase {
  private StringBuffer myBuffer;
  private PendingEventDispatcher<Listener> myDispatcher;

  private interface Listener extends EventListener {
    void foo();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myBuffer = new StringBuffer();
    myDispatcher = PendingEventDispatcher.create(Listener.class);
  }

  @Override
  protected void tearDown() throws Exception {
    myDispatcher = null;
    myBuffer = null;
    super.tearDown();
  }

  public void testRemoveListener1() {
    Listener listener1 = new Listener() {
      @Override
      public void foo() {
        myBuffer.append("[1]");
        removeListener(this);
      }
    };
    Listener listener2 = new Listener() {
      @Override
      public void foo() {
        myBuffer.append("[2]");
      }
    };
    final Listener listener3 = new Listener() {
      @Override
      public void foo() {
        myBuffer.append("[3]");
      }
    };
    addListener(listener1);
    addListener(listener2);
    addListener(listener3);

    String result = "[1][2][3]";
    checkResult(result);
  }

  public void testRemoveListener2() {
    final Listener listener3 = new Listener() {
      @Override
      public void foo() {
        myBuffer.append("[3]");
      }
    };
    Listener listener1 = new Listener() {
      @Override
      public void foo() {
        myBuffer.append("[1]");
        removeListener(listener3);
      }
    };
    Listener listener2 = new Listener() {
      @Override
      public void foo() {
        myBuffer.append("[2]");
      }
    };
    addListener(listener1);
    addListener(listener2);
    addListener(listener3);

    String result = "[1][2]";
    checkResult(result);
  }

  public void testDispatchPending() {
    final Listener listener3 = new Listener() {
      @Override
      public void foo() {
        myBuffer.append("[3]");
      }
    };
    Listener listener1 = new Listener() {
      @Override
      public void foo() {
        myBuffer.append("[1.1]");
        dispatchPendingEvent(listener3);
        myBuffer.append("[1.2]");
      }
    };
    Listener listener2 = new Listener() {
      @Override
      public void foo() {
        myBuffer.append("[2]");
      }
    };
    addListener(listener1);
    addListener(listener2);
    addListener(listener3);

    String result = "[1.1][3][1.2][2]";
    checkResult(result);
  }

  public void testRemoveInDispatchPending() {
    final Listener listener3 = new Listener() {
      @Override
      public void foo() {
        myBuffer.append("[3]");
        removeListener(this);
      }
    };
    Listener listener1 = new Listener() {
      @Override
      public void foo() {
        myBuffer.append("[1.1]");
        dispatchPendingEvent(listener3);
        myBuffer.append("[1.2]");
      }
    };
    Listener listener2 = new Listener() {
      @Override
      public void foo() {
        myBuffer.append("[2]");
      }
    };
    addListener(listener1);
    addListener(listener2);
    addListener(listener3);

    String result = "[1.1][3][1.2][2]";
    checkResult(result);
  }

  private void addListener(Listener listener) {
    myDispatcher.addListener(listener);
  }

  private void removeListener(Listener listener) {
    myDispatcher.removeListener(listener);
  }

  private void dispatchPendingEvent(final Listener listener) {
    myDispatcher.dispatchPendingEvent(listener);
  }

  private void checkResult(String result) {
    myDispatcher.getMulticaster().foo();
    assertEquals(result, myBuffer.toString());
  }
}

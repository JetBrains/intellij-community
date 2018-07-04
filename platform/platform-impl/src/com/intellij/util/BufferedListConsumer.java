/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class BufferedListConsumer<T> implements Consumer<List<T>> {
  private final int myInterval;
  private long myTs;
  private final int mySize;
  private List<T> myBuffer;
  private final Object myFlushLock;
  private final Consumer<List<T>> myConsumer;
  private int myCnt;
  private Runnable myFlushListener;
  private volatile boolean myPendingFlush;

  public BufferedListConsumer(int size, Consumer<List<T>> consumer, int interval) {
    mySize = size;
    myFlushLock = new Object();
    myBuffer = new ArrayList<>(size);
    myConsumer = consumer;
    myInterval = interval;
    myTs = System.currentTimeMillis();
    myCnt = 0;
    myPendingFlush = false;
  }

  public void consumeOne(final T t) {
    synchronized (myFlushLock) {
      ++ myCnt;
      myBuffer.add(t);
      flushCheck();
    }
  }

  @Override
  public void consume(List<T> list) {
    synchronized (myFlushLock) {
      myCnt += list.size();
      myBuffer.addAll(list);
      flushCheck();
    }
  }

  private void flushCheck() {
    final long ts = System.currentTimeMillis();
    if ((myBuffer.size() >= mySize) || (myInterval > 0) && ((ts - myInterval) > myTs)) {
      flushImpl(ts);
    }
  }

    // we need to 1) pass information to consumer in another thread since consumer potentially has heavy consume() - with info linking (git log)
    // 2) requests passed to background thread can come in wrong order -> solution: initiate pooled thread requests which would take what we have currently
    // 3) time is updated at the moment of flush -> buffer is empty at that moment
  private void flushImpl(final long ts) {
    synchronized (myFlushLock) {
      if (myPendingFlush || myBuffer.isEmpty()) return;
      myPendingFlush = true;
      invokeConsumer(createConsumerRunnable(ts));
    }
  }

  protected void invokeConsumer(@NotNull Runnable consumerRunnable) {
    ApplicationManager.getApplication().executeOnPooledThread(consumerRunnable);
  }

  @NotNull
  private Runnable createConsumerRunnable(final long ts) {
    return () -> {
      myTs = ts;
      final List<T> list;
      synchronized (myFlushLock) {
        myPendingFlush = false;
        if (myBuffer.isEmpty()) return;
        list = myBuffer;
        myBuffer = new ArrayList<>(mySize);
      }
      myConsumer.consume(list);
    };
  }

  public void flush() {
    flushImpl(System.currentTimeMillis());
    if (myFlushListener != null) {
      myFlushListener.run();
    }
  }

  public int getCnt() {
    synchronized (myFlushLock) {
      return myCnt;
    }
  }

  public Consumer<T> asConsumer() {
    return t -> consumeOne(t);
  }
}

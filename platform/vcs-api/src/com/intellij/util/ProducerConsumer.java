/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.util.concurrency.Semaphore;

import java.util.ArrayDeque;

/**
 * @author irengrig
 *         Date: 7/5/11
 *         Time: 1:48 AM
 */
public class ProducerConsumer<T> {
  public static final int ourDefaultMaxSize = 20;

  private final ArrayDeque<T> myQueue;
  private final Consumer<T> myConsumer;
  private final int myMaxSize;
  private final Object myLock;
  private final ConsumerRunnable myConsumerThread;
  private boolean myIsAlive;

  public ProducerConsumer(final Consumer<T> consumer) {
    this(consumer, ourDefaultMaxSize);
  }

  public void start() {
    myIsAlive = true;
    myConsumerThread.start();
  }

  public void stop() {
    synchronized (myLock) {
      myIsAlive = false;
      myLock.notifyAll();
    }
  }

  public ProducerConsumer(final Consumer<T> consumer, final int maxSize) {
    this(consumer, maxSize, false);
  }

  public ProducerConsumer(final Consumer<T> consumer, final int maxSize, final boolean onPooledThread) {
    myConsumer = consumer;
    myQueue = new ArrayDeque<T>();
    myMaxSize = maxSize;
    myLock = new Object();

    if (onPooledThread) {
      myConsumerThread = new PooledConsumerRunnable();
      ApplicationManager.getApplication().executeOnPooledThread(myConsumerThread);
    } else {
      myConsumerThread = new ConsumerRunnable();
    }
  }

  private class PooledConsumerRunnable extends ConsumerRunnable {
    private final Semaphore mySemaphore;

    private PooledConsumerRunnable() {
      mySemaphore = new Semaphore();
      mySemaphore.down();
    }

    public void start() {
      mySemaphore.up();
    }

    @Override
    protected void waitForStart() {
      mySemaphore.waitFor();
    }
  }

  private class ConsumerRunnable implements Runnable {
    private Thread myThread;
    
    public void start() {
      myThread.start();
    }

    public void setThread(Thread thread) {
      myThread = thread;
    }

    @Override
    public void run() {
      waitForStart();
      synchronized (myLock) {
        while (myIsAlive) {
          if (! myQueue.isEmpty()) {
            myConsumer.consume(myQueue.removeFirst());
          } else {
            try {
              myLock.wait(10);
            }
            catch (InterruptedException e) {
              //
            }
          }
        }
      }
    }

    protected void waitForStart() {
    }
  }

  public void produce(final T t) {
    synchronized (myLock) {
      while (myQueue.size() >= myMaxSize) {
        try {
          myLock.notifyAll();
          myLock.wait(10);
        }
        catch (InterruptedException e) {
          //
        }
      }
      myQueue.addLast(t);
      myLock.notifyAll();
    }
  }
}

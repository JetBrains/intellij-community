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
package com.intellij.cvsSupport2.javacvsImpl.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.concurrency.Semaphore;
import org.netbeans.lib.cvsclient.ICvsCommandStopper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * author: lesya
 */
public class ReadThread implements Runnable {

  public final static Collection<ReadThread> READ_THREADS = Collections.synchronizedCollection(new ArrayList<ReadThread>());

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.javacvsImpl.io.ReadThread");

  private static final int INITIAL_BUFFER_SIZE = 128 * 1024;
  private static final int TIMEOUT = 3000;
  private static final int END_OF_STREAM = -1;

  private boolean myAtEndOfStream = false;
  private final ICvsCommandStopper myCvsCommandStopper;
  private final byte[] myBuffer = new byte[INITIAL_BUFFER_SIZE];
  private final byte[] myReadBuffer = new byte[INITIAL_BUFFER_SIZE];
  private int myFirstIndex = 0;
  private int myLastIndex = 0;
  private IOException myException;
  private final InputStream myInputStream;
  private final Semaphore myStarted = new Semaphore();
  private boolean myIsClosed = false;

  public ReadThread(InputStream inputStream, ICvsCommandStopper cvsCommandStopper) {
    myInputStream = inputStream;
    myCvsCommandStopper = cvsCommandStopper;
    READ_THREADS.add(this);
  }

  public void prepareForWait() {
    myStarted.down();
  }

  public void waitForStart(){
    myStarted.waitFor();
  }

  public String toString() {
    return super.toString() +
           ", atEnd: " + myAtEndOfStream + ", firstIndex: " + myFirstIndex + ", lastIndex: " + myLastIndex +
           ", exception: " + myException + ", closed: " + myIsClosed;
  }

  public void run() {
    Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
    try {
      if (LOG.isDebugEnabled()) LOG.info("Starting CvsReadThread " + this);
      while (true) {
        try {
          waitForRead();
          if (myAtEndOfStream || (myException != null)) {
            executionCompleted();
            return;
          }
          int result = myInputStream.read(myReadBuffer);
          if (result > 0) {
            writeAndNotify(result);
          }
          else if (result == END_OF_STREAM) {
            detectEndAndNotify();
            return;
          }
        }
        catch (IOException e) {
          detectExceptionAndNotify(e);
          return;
        }
        catch (Throwable t) {
          detectExceptionAndNotify(new IOException(t.getLocalizedMessage()));
          return;
        }
      }
    }
    finally {
      Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
    }
  }

  public synchronized int read() throws IOException {
    int result = waitForAvailableBytes();
    if (result == END_OF_STREAM) return END_OF_STREAM;
    return internalRead();
  }

  public synchronized int read(byte b[], int off, int len) throws IOException {
    int result = waitForAvailableBytes();
    if (result == END_OF_STREAM) return END_OF_STREAM;
    return internalRead(b, off, len);
  }

  public synchronized long skip(long n) throws IOException {
    int result = waitForAvailableBytes();
    if (result == END_OF_STREAM) return END_OF_STREAM;
    return internalSkip(n);
  }

  public synchronized int available() throws IOException {
    if (size() > 0) return size();
    if (myAtEndOfStream) return END_OF_STREAM;
    return myInputStream.available();
  }

  private int waitForAvailableBytes() throws IOException {
    while (size() == 0 && !myAtEndOfStream) {
      try {
        notify();
        wait(TIMEOUT);
      }
      catch (InterruptedException e) {
        throw new IOException(e.getLocalizedMessage());
      }
      if (size() == 0 && !myAtEndOfStream) {
        if (myCvsCommandStopper.isAborted()) {
          throw new ProcessCanceledException();
        }
      }
    }
    if (myException != null) throw myException;
    if (myAtEndOfStream && (size() == 0)) {
      return END_OF_STREAM;
    }
    return -2;
  }

  private synchronized void detectExceptionAndNotify(IOException e) {
    LOG.info(e);
    myException = e;
    executionCompleted();
    notify();
  }

  private synchronized void detectEndAndNotify() {
    if (!myAtEndOfStream) {
      myAtEndOfStream = true;
      notify();      
    }
    executionCompleted();
  }

  private synchronized void writeAndNotify(int result) {
    synchronized (this) {
      if (size() == 0) {
        myFirstIndex = 0;
        myLastIndex = 0;
      }
      System.arraycopy(myReadBuffer, 0, myBuffer, myLastIndex, result);
      myLastIndex += result;
    }
    notify();
  }

  private synchronized void waitForRead() throws InterruptedException {
    myStarted.up();
    if (myAtEndOfStream || (myException != null)) {
      return;
    }    
    wait();
  }

  private void executionCompleted() {
    READ_THREADS.remove(this);
    if (LOG.isDebugEnabled()) LOG.info("Stopping CvsReadThread " + this);
  }

  private int size() {
    return myLastIndex - myFirstIndex;
  }

  public synchronized void close() {
    myIsClosed = true;
    if (myAtEndOfStream) return;
    myAtEndOfStream = true;
    notify();
  }

  private synchronized int internalRead() {
    try {
      return (char)myBuffer[myFirstIndex++];
    }
    finally {
      if (myFirstIndex > myLastIndex) {
        LOG.assertTrue(false);
      }
    }
  }

  private synchronized int internalRead(byte b[], int off, int len) {
    int result = Math.min(len, size());
    System.arraycopy(myBuffer, myFirstIndex, b, off, result);
    myFirstIndex += result;
    return result;
  }

  private long internalSkip(long n) {
    long result = Math.min(n, size());
    myFirstIndex += result;
    return result;
  }
}

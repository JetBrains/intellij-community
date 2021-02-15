// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * This is the 3rd version of SwingWorker (also known as
 * SwingWorker 3), an abstract class that you subclass to
 * perform GUI-related work in a dedicated thread.  For
 * instructions on using this class, see:
 *
 * http://java.sun.com/docs/books/tutorial/uiswing/misc/threads.html
 *
 * Note that the API changed slightly in the 3rd version:
 * You must now invoke start() on the SwingWorker after
 * creating it.
 */
public abstract class SwingWorker<T> {
  private static final Logger LOG = Logger.getInstance(SwingWorker.class);
  private Future<?> myFuture;
  private volatile T value;
  // see getValue(), setValue()
  private final ModalityState myModalityState;

  /**
   * Get the value produced by the worker thread, or null if it
   * hasn't been constructed yet.
   */
  protected T getValue() {
    return value;
  }

  /**
   * Set the value produced by worker thread
   */
  private void setValue(T x) {
    value = x;
  }

  /**
   * Compute the value to be returned by the {@code get} method.
   */
  public abstract T construct();

  /**
   * Called on the event dispatching thread (not on the worker thread)
   * after the {@code construct} method has returned successfully.
   */
  public void finished() {
  }

  /**
   * Called in the worker thread in case a RuntimeException or Error occurred
   * if the {@code construct} method has thrown an uncaught Throwable.
   */
  public void onThrowable() {
  }

  /**
   * A new method that interrupts the worker thread.  Call this method
   * to force the worker to stop what it's doing.
   */
  public void interrupt() {
    myFuture.cancel(true);
  }

  /**
   * Return the value created by the {@code construct} method.
   * Returns null if either the constructing thread or the current
   * thread was interrupted before a value was produced.
   *
   * @return the value created by the {@code construct} method
   */
  public T get() {
    if (myFuture == null) {
      throw new IllegalStateException("The start method has not been called.");
    }
    try {
      myFuture.get();
      return getValue();
    }
    catch (ExecutionException | InterruptedException e) {
      Thread.currentThread().interrupt();
      // propagate
      return null;
    }
  }

  public SwingWorker() {
    myModalityState = ModalityState.current();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Created SwingWorker " + this + " with modality state " + myModalityState);
    }
  }

  /**
   * Start the worker thread.
   */
  public void start() {
    myFuture = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        setValue(construct());
        if (LOG.isDebugEnabled()) {
          LOG.debug("construct() terminated for " + this);
        }
      }
      catch (Throwable e) {
        LOG.error(e);
        onThrowable();
        throw new RuntimeException(e);
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("invoking 'finished' action for " + this);
      }
      ApplicationManager.getApplication().invokeLater(() -> finished(), myModalityState);
    });
  }
}

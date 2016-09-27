/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.application.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ModalityStateListener;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.SkipInHeadlessEnvironment;
import com.intellij.util.containers.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.intellij.openapi.application.impl.LaterInvocator.enterModal;
import static com.intellij.openapi.application.impl.LaterInvocator.invokeLater;
import static com.intellij.openapi.application.impl.LaterInvocator.leaveModal;
import static com.intellij.openapi.application.impl.LaterInvocatorTest.flushSwingQueue;

@SuppressWarnings({"SSBasedInspection"})
@SkipInHeadlessEnvironment
public class PerProjectLaterInvocatorTest extends PlatformTestCase {

  private final static Object lock = new Object();
  @SuppressWarnings("EmptySynchronizedStatement") private static Runnable blockEDT = () -> {synchronized(lock){}};

  private JFrame myFrame = new JFrame("Project frame");
  private Dialog myPerProjectModalDialog = new Dialog(null, "Per-project modal dialog", Dialog.ModalityType.DOCUMENT_MODAL);
  private Dialog myApplicationModalDialog = new Dialog(null, "Owned dialog", Dialog.ModalityType.DOCUMENT_MODAL);

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  private static class Testable {

    private AtomicBoolean isRun = new AtomicBoolean(false);
    private Consumer<Exception> myExceptionConsumer;
    private java.util.List<Runnable> tasksToExecute = Collections.synchronizedList(new ArrayList<>());

    private boolean waitSuspendedEDT = false;

    static Testable creatTestable() {
      return new Testable();
    }

    private Testable suspendEDT(){
      waitSuspendedEDT = true;
      return this;
    }

    private Testable execute (Runnable runnable) {
      if (!waitSuspendedEDT) {
        SwingUtilities.invokeLater(runnable);
      } else {
       tasksToExecute.add(runnable);
      }
      return this;
    }

    private Testable flushEDT() {
      return execute(() -> {
        flushSwingQueue();
        flushSwingQueue();
      });
    }

    private Testable continueEDT() {
      if (!waitSuspendedEDT) throw new RuntimeException("EDT has not been suspended.");
      synchronized (lock) {
        SwingUtilities.invokeLater(blockEDT);
        SwingUtilities.invokeLater(
          () ->  {
            try {
              tasksToExecute.forEach(Runnable::run);
            } catch (Exception e) {
              myExceptionConsumer.accept(e);
            }
          }
        );
      }
      return this;
    }

    private Testable ifExceptions (@NotNull Consumer<Exception> consumer) {
      myExceptionConsumer = consumer;
      return this;
    }

    private Testable ifPass (@NotNull Runnable completionAction) {
      if (!isRun.get()) throw new RuntimeException("Testable has not been run yet");
      completionAction.run();
      return this;
    }
  }

  private static class NumberedRunnable implements Runnable {
    private Integer myNumber;
    private Consumer<Integer> myConsumer;
    private NumberedRunnable(@NotNull Integer number, Consumer<Integer> consumer) {
      myConsumer = consumer;
      myNumber = number;
    }
    private NumberedRunnable(@NotNull Integer number) {
      this(number, null);
    }
    static NumberedRunnable withNumber (@NotNull Integer number) {
      return new NumberedRunnable(number);
    }
    static NumberedRunnable withNumber (@NotNull Integer number, @NotNull Consumer<Integer> consumer) {
      return new NumberedRunnable(number, consumer);
    }
    @Override
    public void run() {
      if (myConsumer != null) myConsumer.accept(myNumber);
    }
  }

  public void testModalityStackApplicationProjectApplication () {

    Integer [] expectedOrder = {1, 2, 4, 3, 5};
    java.util.List<Integer> actualIntegers = Collections.synchronizedList(new ArrayList<Integer>());

    Testable.creatTestable()
      .suspendEDT()
      .execute(() -> invokeLater(NumberedRunnable.withNumber(1, actualIntegers::add), ModalityState.NON_MODAL))
      .flushEDT()
      .execute(() -> enterModal(myApplicationModalDialog))
      .flushEDT()
      .execute(() -> invokeLater(NumberedRunnable.withNumber(2, actualIntegers::add), ModalityState.current()))
      .flushEDT()
      .execute(() -> enterModal(myProject, myPerProjectModalDialog))
      .flushEDT()
      .execute(() -> invokeLater(NumberedRunnable.withNumber(3, actualIntegers::add), ModalityState.NON_MODAL))
      .flushEDT()
      .execute(() -> invokeLater(NumberedRunnable.withNumber(4, actualIntegers::add), ModalityState.current()))
      .flushEDT()
      .execute(() -> leaveModal(myProject, myPerProjectModalDialog))
      .flushEDT()
      .execute(() -> invokeLater(NumberedRunnable.withNumber(5, actualIntegers::add), ModalityState.NON_MODAL))
      .flushEDT()
      .execute(() -> leaveModal(myApplicationModalDialog))
      .flushEDT()
      .execute(() -> {
        Object[] array = actualIntegers.toArray();
        if (!Arrays.equals(expectedOrder, array)) {
          throw new RuntimeException(Arrays.toString(array));
        }
      })
      .continueEDT()
      .ifExceptions(exception -> fail(exception.toString()));

  }

  public void testModalityStateChangedListener () {

    boolean [] enteringOrder = new boolean [] {true, true, false, false};

    AtomicInteger enteringIndex = new AtomicInteger(-1);

    ModalityStateListener modalityStateListener = entering -> {
      if (entering != enteringOrder[enteringIndex.incrementAndGet()])
        throw new RuntimeException("Trying to enter a modal state. Wrong order of the modalityStateListener invocations. Step #" + enteringIndex.get());
    };

    Disposable emptyDisposal = () -> {};

    LaterInvocator.addModalityStateListener(modalityStateListener, emptyDisposal);

    Runnable removeModalityListener = () -> {
      LaterInvocator.removeModalityStateListener(modalityStateListener);
    };

    Testable.creatTestable()
      .suspendEDT()
      .execute(() -> invokeLater(NumberedRunnable.withNumber(1), ModalityState.NON_MODAL))
      .flushEDT()
      .execute(() -> enterModal(myApplicationModalDialog))
      .flushEDT()
      .execute(() -> invokeLater(NumberedRunnable.withNumber(2), ModalityState.current()))
      .flushEDT()
      .execute(() -> enterModal(myProject, myPerProjectModalDialog))
      .flushEDT()
      .execute(() -> invokeLater(NumberedRunnable.withNumber(3), ModalityState.NON_MODAL))
      .flushEDT()
      .execute(() -> invokeLater(NumberedRunnable.withNumber(4), ModalityState.current()))
      .flushEDT()
      .execute(() -> leaveModal(myProject, myPerProjectModalDialog))
      .flushEDT()
      .execute(() -> invokeLater(NumberedRunnable.withNumber(5), ModalityState.NON_MODAL))
      .flushEDT()
      .execute(() -> leaveModal(myApplicationModalDialog))
      .flushEDT()
      .continueEDT()
      .execute(removeModalityListener)
      .ifExceptions(exception -> fail(exception.toString()));

  }
}

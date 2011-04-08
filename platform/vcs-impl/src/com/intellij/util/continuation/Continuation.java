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
package com.intellij.util.continuation;

import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class Continuation {
  private GeneralRunner myGeneralRunner;

  private Continuation(GeneralRunner generalRunner) {
    myGeneralRunner = generalRunner;
  }

  public static Continuation createForCurrentProgress(final Project project, final boolean cancellable, final String commonTitle) {
    return new Continuation(new SameProgressRunner(project, cancellable, commonTitle));
  }

  public static Continuation createFragmented(final Project project, final boolean cancellable) {
    return new Continuation(new SeparatePiecesRunner(project, cancellable));
  }

  public void run(final TaskDescriptor... tasks) {
    if (tasks.length == 0) return;
    myGeneralRunner.next(tasks);

    myGeneralRunner.ping();
  }

  public void run(final List<TaskDescriptor> tasks) {
    if (tasks.isEmpty()) return;
    myGeneralRunner.next(tasks);

    myGeneralRunner.ping();
  }

  public <T extends Exception> void addExceptionHandler(final Class<T> clazz, final Consumer<T> consumer) {
    myGeneralRunner.addExceptionHandler(clazz, consumer);
  }

  public void resume() {
    myGeneralRunner.ping();
  }

  public void clearQueue() {
    myGeneralRunner.cancelEverything();
  }

  public void cancelCurrent() {
    myGeneralRunner.cancelCurrent();
  }

  public void add(List<TaskDescriptor> list) {
    myGeneralRunner.next(list);
  }

  public boolean isEmpty() {
    return myGeneralRunner.isEmpty();
  }

  public void onCancel() {
    myGeneralRunner.onCancel();
  }
}

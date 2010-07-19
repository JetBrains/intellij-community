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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;

public class CalculateContinuation<T> {
  public void calculateAndContinue(final ThrowableComputable<T, Exception> computable, final CatchingConsumer<T, Exception> consumer) {
    final Application application = ApplicationManager.getApplication();

    application.assertIsDispatchThread();

    final Ref<T> t = new Ref<T>();
    application.executeOnPooledThread(new Runnable() {
      public void run() {
        try {
          t.set(computable.compute());
        }
        catch (final Exception e) {
          application.invokeLater(new Runnable() {
            public void run() {
              consumer.consume(e);
            }
          }, ModalityState.NON_MODAL);
        }

        application.invokeLater(new Runnable() {
          public void run() {
            consumer.consume(t.get());
          }
        }, ModalityState.NON_MODAL);
      }
    });
  }
}

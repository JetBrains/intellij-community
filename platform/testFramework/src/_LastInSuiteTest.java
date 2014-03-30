/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.UIUtil;
import junit.framework.TestCase;

import java.lang.reflect.Method;

/**
 * This must be the last test.
 * @author max
 */
@SuppressWarnings("JUnitTestClassNamingConvention")
public class _LastInSuiteTest extends TestCase {
  public void testProjectLeak() throws Exception {
    new WriteCommandAction.Simple(null) {
      @Override
      protected void run() throws Throwable {
        LightPlatformTestCase.closeAndDeleteProject();
      }
    }.execute().throwException();

    // disposes default project too
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ((ApplicationImpl)ApplicationManager.getApplication()).setDisposeInProgress(true);
        LightPlatformTestCase.disposeApplication();
      }
    });

    try {
      LeakHunter.checkProjectLeak();
      Disposer.assertIsEmpty(true);
    }
    catch (Exception e) {
      captureMemorySnapshot();
      throw e;
    }
  }

  private static void captureMemorySnapshot() {
    try {
      Method snapshot = ReflectionUtil.getMethod(Class.forName("com.intellij.util.ProfilingUtil"), "forceCaptureMemorySnapshot");
      if (snapshot != null) {
        snapshot.invoke(null);
      }
    }
    catch (Exception ignored) { }
  }
}

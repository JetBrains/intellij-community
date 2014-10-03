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

import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.psi.impl.DocumentCommitThread;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.ui.UIUtil;
import junit.framework.TestCase;
import sun.awt.AWTAutoShutdown;

/**
 * This is should be first test in all tests so we can measure how long tests are starting up.
 * @author max
 */
@SuppressWarnings("JUnitTestClassNamingConvention")
public class _FirstInSuiteTest extends TestCase {
  public void testNothing() throws Exception {
    // in tests EDT inexplicably shuts down sometimes during the first access,
    // which leads to nasty problems in ApplicationImpl which assumes there is only one EDT.
    // so we try to forcibly terminate EDT here to urge JVM to re-spawn new shiny permanent EDT-1
    UIUtil.invokeAndWaitIfNeeded(EmptyRunnable.getInstance());
    final Thread mainThread = Thread.currentThread();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        AWTAutoShutdown.getInstance().notifyThreadBusy(mainThread);
        LightPlatformTestCase.initApplication();
        DocumentCommitThread.getInstance();
        Thread.currentThread().interrupt(); // exit current EDT, ignore all queued events since they are in a wrong thread by now
      }
    });
  }
}

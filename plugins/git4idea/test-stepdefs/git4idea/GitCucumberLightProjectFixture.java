/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.impl.LightIdeaTestFixtureImpl;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FilenameFilter;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This is a {@link LightIdeaTestFixtureImpl} modification that overrides {@link #shouldContainTempFiles()}.
 * If the method is not overridden, the project directory gets deleted on every {@link UsefulTestCase#tearDown()},
 * but it is not recreated in setUp, because it is a Light test: the project is initialized only once.
 *
 * @author Kirill Likhodedov
 */
@SuppressWarnings({"JUnitTestCaseWithNoTests", "JUnitTestClassNamingConvention"})
class GitCucumberLightProjectFixture extends LightIdeaTestFixtureImpl {

  static {
    // Radar #5755208: Command line Java applications need a way to launch without a Dock icon.
    System.setProperty("apple.awt.UIElement", "true");
  }

  public GitCucumberLightProjectFixture() {
    super(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);
  }

  @Override
  protected boolean shouldDeleteTempFilesOnTearDown() {
    return false;
  }

  @Override
  public void setUp() throws Exception {
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, "PlatformLangXml");
    edt(new ThrowableRunnable<Exception>() {
      @Override
      public void run() throws Exception {
        LightPlatformTestCase.initApplication();
        GitCucumberLightProjectFixture.super.setUp();
      }
    });
  }

  @Override
  public void tearDown() throws Exception {
    // cleanup project dir: although we want to keep the project, we remove the repository and any work that we've done
    File projectDir = new File(getProject().getBasePath());
    File[] nonProjectFiles = projectDir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return !name.endsWith(".ipr");
      }
    });
    for (File file : nonProjectFiles) {
      FileUtil.delete(file);
    }

    edt(new ThrowableRunnable<Exception>() {
      @Override
      public void run() throws Exception {
        GitCucumberLightProjectFixture.super.tearDown();
      }
    });
  }

  private static void edt(@NotNull final ThrowableRunnable<Exception> runnable) throws Exception {
    final AtomicReference<Exception> exception = new AtomicReference<Exception>();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          runnable.run();
        }
        catch (Exception throwable) {
          exception.set(throwable);
        }
      }
    });
    //noinspection ThrowableResultOfMethodCallIgnored
    if (exception.get() != null) {
      throw exception.get();
    }
  }

}

/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.plugins.gradle.internal.daemon.GradleDaemonServices;

/**
 * @author Vladislav.Soroka
 * @since 2/8/2017
 */
public class GradleCleanupService implements Disposable, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance(GradleCleanupService.class);

  @Override
  public void dispose() {
    if(ApplicationManager.getApplication().isUnitTestMode()) return;
    // do not use DefaultGradleConnector.close() it sends org.gradle.launcher.daemon.protocol.StopWhenIdle message and waits
    try {
      GradleDaemonServices.stopDaemons();
    }
    catch (Exception e) {
      LOG.warn("Failed to stop Gradle daemons during IDE shutdown", e);
    }
  }
}

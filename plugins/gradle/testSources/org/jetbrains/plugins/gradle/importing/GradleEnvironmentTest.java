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
package org.jetbrains.plugins.gradle.importing;

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getExternalProjectPath;

/**
 * @author Vladislav.Soroka
 * @since 4/2/2017
 */
@SuppressWarnings("JUnit4AnnotatedMethodInJUnit3TestCase")
public class GradleEnvironmentTest extends GradleImportingTestCase {
  @Test
  @TargetVersions("3.5+")
  public void testGradleEnvCustomization() throws Exception {
    Map<String, String> passedEnv = Collections.singletonMap("FOO", "foo value");
    StringBuilder gradleEnv = new StringBuilder();

    importAndRunTask(passedEnv, gradleEnv);

    assertEquals(DefaultGroovyMethods.toMapString(passedEnv), gradleEnv.toString().trim());
  }

  @Test
  @TargetVersions("3.4")
  public void testGradleEnvCustomizationNotSupported() throws Exception {
    Map<String, String> passedEnv = Collections.singletonMap("FOO", "foo value");
    StringBuilder gradleEnv = new StringBuilder();
    importAndRunTask(passedEnv, gradleEnv);
    assertTrue(gradleEnv.toString().trim().startsWith(
      "The version of Gradle you are using (3.4) does not support the environment variables customization feature. " +
      "Support for this is available in Gradle 3.5 and all later versions."));
  }

  private void importAndRunTask(Map<String, String> passedEnv, StringBuilder gradleEnv) throws IOException {
    importProject("task printEnv() {\n" +
                  "  doLast { println System.getenv().toMapString()}\n" +
                  "}");

    ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
    Module module = getModule("project");
    settings.setExternalProjectPath(getExternalProjectPath(module));
    settings.setTaskNames(Collections.singletonList("printEnv"));
    settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.getId());
    settings.setScriptParameters("--quiet");
    settings.setPassParentEnvs(false);
    settings.setEnv(passedEnv);
    ExternalSystemProgressNotificationManager notificationManager =
      ServiceManager.getService(ExternalSystemProgressNotificationManager.class);
    ExternalSystemTaskNotificationListenerAdapter listener = new ExternalSystemTaskNotificationListenerAdapter() {
      @Override
      public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
        gradleEnv.append(text);
      }
    };
    notificationManager.addNotificationListener(listener);
    try {
      ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID, myProject, GradleConstants.SYSTEM_ID, null,
                                 ProgressExecutionMode.NO_PROGRESS_SYNC);
    }
    finally {
      notificationManager.removeNotificationListener(listener);
    }
  }
}

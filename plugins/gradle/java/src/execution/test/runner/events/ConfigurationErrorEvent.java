// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemProgressEvent;
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.GradleManager;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole;
import org.jetbrains.plugins.gradle.service.project.GradleNotification;
import org.jetbrains.plugins.gradle.service.project.GradleNotificationIdsHolder;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.event.HyperlinkEvent;

/**
 * @author Vladislav.Soroka
 */
public class ConfigurationErrorEvent extends AbstractTestEvent {

  public ConfigurationErrorEvent(GradleTestsExecutionConsole executionConsole) {
    super(executionConsole);
  }

  @Override
  public void process(@NotNull final TestEventXmlView xml) throws TestEventXmlView.XmlParserException {
    @NlsSafe final String errorTitle = xml.getEventTitle();
    final String configurationErrorMsg = xml.getEventMessage();
    final boolean openSettings = xml.isEventOpenSettings();
    final Project project = getProject();
    assert project != null;
    final String message = getConfigurationErrorMessage(configurationErrorMsg, openSettings);
    GradleNotification.NOTIFICATION_GROUP
      .createNotification(errorTitle, message, NotificationType.WARNING)
      .setDisplayId(GradleNotificationIdsHolder.configurationError)
      .setListener(new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          notification.expire();
          if ("Gradle settings".equals(event.getDescription())) {
            ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID);
            assert manager instanceof GradleManager;
            GradleManager gradleManager = (GradleManager)manager;
            Configurable configurable = gradleManager.getConfigurable(project);
            ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
          }
          else {
            BrowserUtil.browse(event.getDescription());
          }
        }
      })
      .notify(project);
  }


  @Nls
  private String getConfigurationErrorMessage(@NlsSafe String configurationErrorMsg, boolean openSettings) {
    if (openSettings) {
      return new HtmlBuilder()
        .append(HtmlChunk.br())
        .append("\n")
        .append(configurationErrorMsg)
        .append(HtmlChunk.link("Gradle settings", GradleBundle.message("gradle.open.gradle.settings")))
        .toString();

    }
    return new HtmlBuilder()
        .append(HtmlChunk.br())
        .append("\n")
        .append(configurationErrorMsg)
      .toString();
  }

  @Override
  public void process(@NotNull ExternalSystemProgressEvent<? extends TestOperationDescriptor> testEvent) {
    // TODO not yet implemented
  }
}

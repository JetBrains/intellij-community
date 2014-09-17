package org.jetbrains.plugins.gradle.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskState;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.project.Project;
import org.gradle.tooling.internal.consumer.connection.NonCancellableConsumerConnectionAdapter;
import org.jetbrains.plugins.gradle.service.notification.OpenGradleSettingsCallback;

import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 8/8/11 11:44 AM
 */
public class GradleLog {

  public static final Logger LOG = Logger.getInstance(GradleLog.class);

  private static final String GRADLE_PROVIDER_DOES_NOT_SUPPORT_CANCELLATION_MESSAGE =
    "Note: Version of Gradle provider does not support cancellation. Upgrade your Gradle build.";

  private GradleLog() {
  }

  public static class GradleLogbackAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent event) {
      ThrowableProxy throwableProxy =
        event.getThrowableProxy() instanceof ThrowableProxy ? (ThrowableProxy)event.getThrowableProxy() : null;
      Throwable throwable = throwableProxy == null ? null : throwableProxy.getThrowable();

      switch (event.getLevel().toInt()) {
        case Level.ALL_INT:
        case Level.TRACE_INT:
        case Level.DEBUG_INT:
          LOG.debug(event.getFormattedMessage(), throwable);
          break;
        case Level.INFO_INT:
          LOG.info(event.getFormattedMessage(), throwable);
          break;
        case Level.WARN_INT:
          LOG.warn(event.getFormattedMessage(), throwable);
          break;
        case Level.ERROR_INT:
          LOG.error(event.getFormattedMessage(), throwable);
          break;
        case Level.OFF_INT:
          break;
        default:
          LOG.debug(event.getFormattedMessage(), throwable);
          break;
      }

      if (NonCancellableConsumerConnectionAdapter.class.getName().equals(event.getLoggerName()) &&
          GRADLE_PROVIDER_DOES_NOT_SUPPORT_CANCELLATION_MESSAGE.equals(event.getMessage())) {
        // see org.gradle.tooling.internal.consumer.connection.NonCancellableConsumerConnectionAdapter#handleCancellationPreOperation

        ExternalSystemProcessingManager processingManager = ServiceManager.getService(ExternalSystemProcessingManager.class);
        final List<ExternalSystemTask> canceledTasks =
          processingManager
            .findTasksOfState(GradleConstants.SYSTEM_ID, ExternalSystemTaskState.CANCELING, ExternalSystemTaskState.CANCELED);
        for (ExternalSystemTask canceledTask : canceledTasks) {
          final Project project = canceledTask.getId().findProject();
          if (project != null) {
            String errorMessage = String.format("%s <a href=\"%s\">Open Gradle settings</a>",
                                                "Configured version of Gradle does not support cancellation. Please, use Gradle 2.1 or newer.\n",
                                                OpenGradleSettingsCallback.ID);
            NotificationData notification = new NotificationData(
              "Gradle supports cancellation since 2.1 version", errorMessage, NotificationCategory.WARNING,
              NotificationSource.PROJECT_SYNC);
            notification.setListener(OpenGradleSettingsCallback.ID, new OpenGradleSettingsCallback(project));
            notification.setBalloonNotification(true);
            ExternalSystemNotificationManager.getInstance(project).showNotification(GradleConstants.SYSTEM_ID, notification);
          }
        }
      }
    }
  }
}

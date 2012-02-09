package org.jetbrains.plugins.gradle.util;

import com.intellij.execution.rmi.RemoteUtil;
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileTypeDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.config.GradleSettings;
import org.jetbrains.plugins.gradle.model.GradleProject;
import org.jetbrains.plugins.gradle.remote.GradleApiException;
import org.jetbrains.plugins.gradle.task.GradleResolveProjectTask;
import org.jetbrains.plugins.gradle.ui.GradleIcons;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;

/**
 * Holds miscellaneous utility methods.
 * 
 * @author Denis Zhdanov
 * @since 8/25/11 1:19 PM
 */
public class GradleUtil {

  private GradleUtil() {
  }

  /**
   * Allows to retrieve file chooser descriptor that filters gradle scripts.
   * <p/>
   * <b>Note:</b> we want to fall back to the standard {@link FileTypeDescriptor} when dedicated gradle file type
   * is introduced (it's processed as groovy file at the moment). We use open project descriptor here in order to show
   * custom gradle icon at the file chooser ({@link GradleIcons#GRADLE_ICON}, is used at the file chooser dialog via 
   * the dedicated gradle project open processor).
   */
  public static FileChooserDescriptor getFileChooserDescriptor() {
    return DescriptorHolder.GRADLE_BUILD_FILE_CHOOSER_DESCRIPTOR;
  }

  /**
   * @param path    target path
   * @return        absolute path that points to the same location as the given one and that uses only slashes
   */
  @NotNull
  public static String toCanonicalPath(@NotNull String path) {
    return PathUtil.getCanonicalPath(new File(path).getAbsolutePath());
  }

  /**
   * Asks to show balloon that contains information related to the given component.
   *  
   * @param component    component for which we want to show information
   * @param messageType  balloon message type
   * @param message      message to show
   */
  public static void showBalloon(@NotNull JComponent component, @NotNull MessageType messageType, @NotNull String message) {
    BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, messageType, null);
    Balloon balloon = balloonBuilder.setFadeoutTime(TimeUnit.SECONDS.toMillis(1)).createBalloon();
    Dimension size = component.getSize();
    Balloon.Position position;
    int x;
    int y;
    if (size == null) {
      x = y = 0;
      position = Balloon.Position.above;
    }
    else {
      x = Math.min(10, size.width / 2);
      y = size.height;
      position = Balloon.Position.below;
    }
    balloon.show(new RelativePoint(component, new Point(x, y)), position);
  }

  /**
   * Delegates to the {@link #refreshProject(Project, String, Ref, boolean, boolean)} with the following defaults:
   * <pre>
   * <ul>
   *   <li>target gradle project path is retrieved from the {@link GradleSettings gradle settings} associated with the given project;</li>
   *   <li>refresh process is run in background;</li>
   *   <li>any problem occurred during the refresh is reported to the {@link GradleLog#LOG};</li>
   * </ul>
   * </pre>
   * 
   * @param project  target intellij project to use
   */
  public static void refreshProject(@NotNull Project project) {
    final GradleSettings settings = GradleSettings.getInstance(project);
    final String linkedProjectPath = settings.LINKED_PROJECT_FILE_PATH;
    if (StringUtil.isEmpty(linkedProjectPath)) {
      return;
    }
    Ref<String> errorHolder = new Ref<String>();
    refreshProject(project, linkedProjectPath, errorHolder, true, false);
    final String error = errorHolder.get();
    if (!StringUtil.isEmpty(error)) {
      GradleLog.LOG.warn(error);
    }
  }

  /**
   * {@link RemoteUtil#unwrap(Throwable) unwraps} given exception if possible and builds error message for it.
   *
   * @param e  exception to process
   * @return   error message for the given exception
   */
  @SuppressWarnings({"ThrowableResultOfMethodCallIgnored", "IOResourceOpenedButNotSafelyClosed"})
  public static String buildErrorMessage(@NotNull Throwable e) {
    Throwable unwrapped = RemoteUtil.unwrap(e);
    String reason = unwrapped.getLocalizedMessage();
    if (!StringUtil.isEmpty(reason)) {
      return reason;
    }
    else if (unwrapped.getClass() == GradleApiException.class) {
      return String.format("gradle api threw an exception: %s", ((GradleApiException)unwrapped).getOriginalReason());
    }
    else {
      StringWriter writer = new StringWriter();
      unwrapped.printStackTrace(new PrintWriter(writer));
      return writer.toString();
    }
  }  
  /**
   * Queries slave gradle process to refresh target gradle project.
   * 
   * @param project            target intellij project to use
   * @param gradleProjectPath  path of the target gradle project's file
   * @param errorHolder        holder for the error message that describes a problem occurred during the refresh (if any)
   * @param resolveLibraries   flag that identifies whether gradle libraries should be resolved during the refresh
   * @return                   the most up-to-date gradle project (if any)
   */
  @Nullable
  public static GradleProject refreshProject(@NotNull final Project project,
                                    @NotNull final String gradleProjectPath,
                                    @NotNull final Ref<String> errorHolder,
                                    final boolean resolveLibraries,
                                    final boolean modal)
  {
    final Ref<GradleProject> gradleProject = new Ref<GradleProject>();
    final TaskUnderProgress task = new TaskUnderProgress() {
      @SuppressWarnings({"ThrowableResultOfMethodCallIgnored", "IOResourceOpenedButNotSafelyClosed"})
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        GradleResolveProjectTask task = new GradleResolveProjectTask(project, gradleProjectPath, resolveLibraries);
        task.execute(indicator);
        gradleProject.set(task.getProject());
        final Throwable error = task.getError();
        if (error == null) {
          return;
        }
        final String message = buildErrorMessage(error);
        errorHolder.set(String.format("Can't resolve gradle project at '%s'. Reason: %s", gradleProjectPath, message));
      }
    };
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (modal) {
          ProgressManager.getInstance().run(new Task.Modal(project, GradleBundle.message("gradle.import.progress.text"), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              task.execute(indicator);
            }
          });
        }
        else {
          ProgressManager.getInstance().run(new Task.Backgroundable(project, GradleBundle.message("gradle.sync.progress.text")) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              task.execute(indicator);
            }
          });
        } 
      }
    });
    return gradleProject.get();
  }
  
  private interface TaskUnderProgress {
    void execute(@NotNull ProgressIndicator indicator);
  }
  
  /**
   * We use this class in order to avoid static initialisation of the wrapped object - it loads number of pico container-based
   * dependencies that are unavailable to the slave gradle project, so, we don't want to get unexpected NPE there.
   */
  private static class DescriptorHolder {
    public static final FileChooserDescriptor GRADLE_BUILD_FILE_CHOOSER_DESCRIPTOR = new OpenProjectFileChooserDescriptor(true) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return GradleConstants.DEFAULT_SCRIPT_NAME.equals(file.getName());
      }

      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        if (!super.isFileVisible(file, showHiddenFiles)) {
          return false;
        }
        return file.isDirectory() || GradleConstants.EXTENSION.equals(file.getExtension());
      }
    };
  }
}

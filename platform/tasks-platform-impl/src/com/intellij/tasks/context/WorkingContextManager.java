// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.context;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskBundle;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.zip.JBZipEntry;
import com.intellij.util.io.zip.JBZipFile;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public final class WorkingContextManager {
  private static final Logger LOG = Logger.getInstance(WorkingContextManager.class);
  @NonNls private static final String TASKS_FOLDER = "tasks";

  private final Project myProject;
  @NonNls private static final String TASKS_ZIP_POSTFIX = ".tasks.zip";
  @NonNls private static final String TASK_XML_POSTFIX = ".task.xml";
  private static final String CONTEXT_ZIP_POSTFIX = ".contexts.zip";
  private static final Comparator<JBZipEntry> ENTRY_COMPARATOR = (o1, o2) -> Long.signum(o2.getTime() - o1.getTime());
  private boolean ENABLED;

  public static WorkingContextManager getInstance(@NotNull Project project) {
    return project.getService(WorkingContextManager.class);
  }

  public WorkingContextManager(@NotNull Project project) {
    myProject = project;
    ENABLED = !ApplicationManager.getApplication().isUnitTestMode();
  }

  @TestOnly
  public void enableUntil(@NotNull Disposable disposable) {
    ENABLED = true;
    Disposer.register(disposable, ()-> ENABLED = false);
  }

  public void loadContext(@NotNull Element fromElement) {
    for (WorkingContextProvider provider : WorkingContextProvider.EP_NAME.getExtensionList()) {
      try {
        Element child = fromElement.getChild(provider.getId());
        if (child != null) {
          provider.loadContext(myProject, child);
        }
      }
      catch (InvalidDataException e) {
        LOG.error(e);
      }
    }
  }

  public void saveContext(Element toElement) {
    for (WorkingContextProvider provider : WorkingContextProvider.EP_NAME.getExtensionList()) {
      try {
        Element child = new Element(provider.getId());
        provider.saveContext(myProject, child);
        toElement.addContent(child);
      }
      catch (WriteExternalException e) {
        LOG.error(e);
      }
    }
  }

  public void clearContext() {
    for (WorkingContextProvider provider : WorkingContextProvider.EP_NAME.getExtensionList()) {
      provider.clearContext(myProject);
    }
  }

  public void saveContext(Task task) {
    String entryName = task.getId() + TASK_XML_POSTFIX;
    saveContext(entryName, TASKS_ZIP_POSTFIX, task.getSummary());
  }

  public void saveContext(@Nullable String entryName, @Nullable String comment) {
    saveContext(entryName, CONTEXT_ZIP_POSTFIX, comment);
  }

  public boolean hasContext(String entryName) {
    return doEntryAction(CONTEXT_ZIP_POSTFIX, entryName, entry -> {});
  }

  private synchronized void saveContext(@Nullable String entryName, String zipPostfix, @Nullable String comment) {
    if (!ENABLED) return;
    try (JBZipFile archive = getTasksArchive(zipPostfix)) {
      if (entryName == null) {
        int i = archive.getEntries().size();
        do {
          entryName = "context" + i++;
        } while (archive.getEntry("/" + entryName) != null);
      }
      JBZipEntry entry = archive.getOrCreateEntry("/" + entryName);
      if (comment != null) {
        entry.setComment(StringUtil.first(comment, 200, true));
      }
      Element element = new Element("context");
      saveContext(element);
      String s = new XMLOutputter().outputString(element);
      entry.setData(s.getBytes(StandardCharsets.UTF_8));
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private JBZipFile getTasksArchive(String postfix) {
    File file = getArchiveFile(postfix);
    try {
      return new JBZipFile(file);
    }
    catch (IOException e) {
      file.delete();
      JBZipFile zipFile = null;
      try {
        zipFile = new JBZipFile(file);
        Notifications.Bus.notify(new Notification("Tasks", TaskBundle.message("notification.title.context.data.corrupted"),
                                                  TaskBundle.message("notification.content.context.information.history", myProject.getName()), NotificationType.ERROR), myProject);
      }
      catch (IOException e1) {
        LOG.error("Can't repair form context data corruption", e1);
      }
      return zipFile;
    }
  }

  private File getArchiveFile(String postfix) {
    File tasksFolder = PathManager.getConfigDir().resolve(TASKS_FOLDER).toFile();
    if (!tasksFolder.exists()) {
      //noinspection ResultOfMethodCallIgnored
      tasksFolder.mkdirs();
    }
    String projectName = FileUtil.sanitizeFileName(myProject.getName());
    return new File(tasksFolder, projectName + postfix);
  }

  public void restoreContext(@NotNull Task task) {
    loadContext(TASKS_ZIP_POSTFIX, task.getId() + TASK_XML_POSTFIX);
  }

  private boolean loadContext(String zipPostfix, String entryName) {
    return doEntryAction(zipPostfix, entryName, entry -> {
      String s = new String(entry.getData(), StandardCharsets.UTF_8);
      loadContext(JDOMUtil.load(s));
    });
  }

  private synchronized boolean doEntryAction(String zipPostfix, String entryName, ThrowableConsumer<JBZipEntry, Exception> action) {
    if (!ENABLED) return false;

    try (JBZipFile archive = getTasksArchive(zipPostfix)) {
      JBZipEntry entry = archive.getEntry(StringUtil.startsWithChar(entryName, '/') ? entryName : "/" + entryName);
      if (entry != null) {
        action.consume(entry);
        return true;
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return false;
  }

  public List<ContextInfo> getContextHistory() {
    return getContextHistory(CONTEXT_ZIP_POSTFIX);
  }

  private synchronized List<ContextInfo> getContextHistory(String zipPostfix) {
    if (!ENABLED) return Collections.emptyList();
    try (JBZipFile archive = getTasksArchive(zipPostfix)) {
      List<JBZipEntry> entries = archive.getEntries();
      return ContainerUtil.mapNotNull(entries, entry -> entry.getName().startsWith("/context") ? new ContextInfo(entry.getName(), entry.getTime(), entry.getComment()) : null);
    }
    catch (Exception e) {
      LOG.error(e);
      return Collections.emptyList();
    }
  }

  public boolean loadContext(String name) {
    return loadContext(CONTEXT_ZIP_POSTFIX, name);
  }

  public void removeContext(String name) {
    removeContext(name, CONTEXT_ZIP_POSTFIX);
  }

  public void removeContext(Task task) {
    removeContext(task.getId(), TASKS_ZIP_POSTFIX);
  }

  private void removeContext(String name, String postfix) {
    if (!ENABLED) return;
    try (JBZipFile archive = getTasksArchive(postfix)) {
      JBZipEntry entry = archive.getEntry(name);
      if (entry != null) {
        archive.eraseEntry(entry);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public void pack(int max, int delta) {
    pack(max, delta, CONTEXT_ZIP_POSTFIX);
    pack(max, delta, TASKS_ZIP_POSTFIX);
  }

  private synchronized void pack(int max, int delta, String zipPostfix) {
    if (!ENABLED) return;
    try (JBZipFile archive = getTasksArchive(zipPostfix)) {
      List<JBZipEntry> entries = archive.getEntries();
      if (entries.size() > max + delta) {
        JBZipEntry[] array = entries.toArray(new JBZipEntry[0]);
        Arrays.sort(array, ENTRY_COMPARATOR);
        for (int i = array.length - 1; i >= max; i--) {
          archive.eraseEntry(array[i]);
        }
        archive.gc();
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @TestOnly
  public File getContextFile() {
    return getArchiveFile(CONTEXT_ZIP_POSTFIX);
  }

  @TestOnly
  public File getTaskFile() {
    return getArchiveFile(TASKS_ZIP_POSTFIX);
  }
}

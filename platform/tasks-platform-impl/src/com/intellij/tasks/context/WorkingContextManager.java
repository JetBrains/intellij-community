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

package com.intellij.tasks.context;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.tasks.Task;
import com.intellij.util.JdomKt;
import com.intellij.util.NullableFunction;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class WorkingContextManager {

  private static final Logger LOG = Logger.getInstance("#com.intellij.tasks.context.WorkingContextManager");
  @NonNls private static final String TASKS_FOLDER = "tasks";

  private final Project myProject;
  @NonNls private static final String TASKS_ZIP_POSTFIX = ".tasks.zip";
  @NonNls private static final String TASK_XML_POSTFIX = ".task.xml";
  private static final String CONTEXT_ZIP_POSTFIX = ".contexts.zip";
  private static final Comparator<JBZipEntry> ENTRY_COMPARATOR = (o1, o2) -> Long.signum(o2.getTime() - o1.getTime());

  public static WorkingContextManager getInstance(Project project) {
    return ServiceManager.getService(project, WorkingContextManager.class);
  }

  public WorkingContextManager(Project project) {
    myProject = project;
  }

  public void loadContext(Element fromElement) {
    for (WorkingContextProvider provider : Extensions.getExtensions(WorkingContextProvider.EP_NAME, myProject)) {
      try {
        Element child = fromElement.getChild(provider.getId());
        if (child != null) {
          provider.loadContext(child);
        }
      }
      catch (InvalidDataException e) {
        LOG.error(e);
      }
    }
  }

  public void saveContext(Element toElement) {
    for (WorkingContextProvider provider : Extensions.getExtensions(WorkingContextProvider.EP_NAME, myProject)) {
      try {
        Element child = new Element(provider.getId());
        provider.saveContext(child);
        toElement.addContent(child);
      }
      catch (WriteExternalException e) {
        LOG.error(e);
      }
    }
  }

  public void clearContext() {
    for (WorkingContextProvider provider : Extensions.getExtensions(WorkingContextProvider.EP_NAME, myProject)) {
      provider.clearContext();
    }
  }

  public void saveContext(Task task) {
    String entryName = task.getId() + TASK_XML_POSTFIX;
    saveContext(entryName, TASKS_ZIP_POSTFIX, task.getSummary());
  }

  public void saveContext(@Nullable String entryName, @Nullable String comment) {
    saveContext(entryName, CONTEXT_ZIP_POSTFIX, comment);
  }

  private synchronized void saveContext(@Nullable String entryName, String zipPostfix, @Nullable String comment) {
    JBZipFile archive = null;
    try {
      archive = getTasksArchive(zipPostfix);
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
      entry.setData(s.getBytes(CharsetToolkit.UTF8_CHARSET));
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      closeArchive(archive);
    }
  }

  private JBZipFile getTasksArchive(String postfix) throws IOException {
    File file = getArchiveFile(postfix);
    try {
      return new JBZipFile(file);
    }
    catch (IOException e) {
      file.delete();
      JBZipFile zipFile = null;
      try {
        zipFile = new JBZipFile(file);
        Notifications.Bus.notify(new Notification("Tasks", "Context Data Corrupted",
                                                  "Context information history for " + myProject.getName() + " was corrupted.\n" +
                                                  "The history was replaced with empty one.", NotificationType.ERROR), myProject);
      }
      catch (IOException e1) {
        LOG.error("Can't repair form context data corruption", e1);
      }
      return zipFile;
    }
  }

  private File getArchiveFile(String postfix) {
    File tasksFolder = new File(PathManager.getConfigPath(), TASKS_FOLDER);
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

  private synchronized boolean loadContext(String zipPostfix, String entryName) {
    JBZipFile archive = null;
    try {
      archive = getTasksArchive(zipPostfix);
      JBZipEntry entry = archive.getEntry(StringUtil.startsWithChar(entryName, '/') ? entryName : "/" + entryName);
      if (entry != null) {
        String s = new String(entry.getData(), CharsetToolkit.UTF8_CHARSET);
        loadContext(JdomKt.loadElement(s));
        return true;
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
    finally {
      closeArchive(archive);
    }
    return false;
  }

  private static void closeArchive(JBZipFile archive) {
    if (archive != null) {
      try {
        archive.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  public List<ContextInfo> getContextHistory() {
    return getContextHistory(CONTEXT_ZIP_POSTFIX);
  }

  private synchronized List<ContextInfo> getContextHistory(String zipPostfix) {
    JBZipFile archive = null;
    try {
      archive = getTasksArchive(zipPostfix);
      List<JBZipEntry> entries = archive.getEntries();
      return ContainerUtil.mapNotNull(entries, (NullableFunction<JBZipEntry, ContextInfo>)entry -> entry.getName().startsWith("/context") ? new ContextInfo(entry.getName(), entry.getTime(), entry.getComment()) : null);
    }
    catch (IOException e) {
      LOG.error(e);
      return Collections.emptyList();
    }
    finally {
      closeArchive(archive);
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
    JBZipFile archive = null;
    try {
      archive = getTasksArchive(postfix);
      JBZipEntry entry = archive.getEntry(name);
      if (entry != null) {
        archive.eraseEntry(entry);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      closeArchive(archive);
    }
  }

  public void pack(int max, int delta) {
    pack(max, delta, CONTEXT_ZIP_POSTFIX);
    pack(max, delta, TASKS_ZIP_POSTFIX);
  }

  private synchronized void pack(int max, int delta, String zipPostfix) {
    JBZipFile archive = null;
    try {
      archive = getTasksArchive(zipPostfix);
      List<JBZipEntry> entries = archive.getEntries();
      if (entries.size() > max + delta) {
        JBZipEntry[] array = entries.toArray(new JBZipEntry[entries.size()]);
        Arrays.sort(array, ENTRY_COMPARATOR);
        for (int i = array.length - 1; i >= max; i--) {
          archive.eraseEntry(array[i]);
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      closeArchive(archive);
    }
  }

  @TestOnly
  public File getContextFile() {
    return getArchiveFile(CONTEXT_ZIP_POSTFIX);
  }
}

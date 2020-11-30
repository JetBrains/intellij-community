// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.win;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.function.BiConsumer;

final class WinShellIntegrationCache {
  static final class IdeEntry {
    IdeEntry(@Nullable String appUserModelId,
             @Nullable Path startMenuShellLinkPath,
             @Nullable Path taskbarShellLinkPath) {
      this.appUserModelId = appUserModelId;
      this.startMenuShellLinkPath = startMenuShellLinkPath;
      this.taskbarShellLinkPath = taskbarShellLinkPath;
    }


    final @Nullable String appUserModelId;
    final @Nullable Path startMenuShellLinkPath;
    final @Nullable Path taskbarShellLinkPath;
  }


  static final int MAX_ENTRIES = 10;


  @SuppressWarnings("DuplicateThrows")
  static @NotNull WinShellIntegrationCache loadFromStorage(@NotNull Path storageDirPath) throws JDOMException,
                                                                                                FileNotFoundException,
                                                                                                IOException {
    final @NotNull Element root;
    try (final var stream = new FileInputStream(storageDirPath.resolve(CACHE_FILE_NAME).toFile());
         final var channel = stream.getChannel()) {
      final var lock = channel.lock(0, Long.MAX_VALUE, true);
      root = JDOMUtil.load(stream);
    }

    final @NotNull LinkedHashMap<Path, IdeEntry> cacheEntries = new LinkedHashMap<>(MAX_ENTRIES + 1);
    final @NotNull var cacheEntriesSet = cacheEntries.entrySet();

    for (Element child : root.getChildren()) {
      final String childName = child.getName();

      if (!IDE_ENTRY_NAME.equals(childName)) {
        continue;
      }

      final String idePathStr = child.getAttributeValue(IDE_PATH_ATTRIBUTE_NAME);
      if (idePathStr == null) {
        continue;
      }

      final String appUserModelId = child.getChildText(IDE_APPUSERMODELID_CHILD_NAME);
      if (StringUtilRt.isEmptyOrSpaces(appUserModelId)) {
        continue;
      }

      final @Nullable String startMenuShellLinkPathStr = child.getChildText(IDE_STARTMENUSHELLLINKPATH_CHILD_NAME);
      final @Nullable String taskbarShellLinkPathStr = child.getChildText(IDE_TASKBARSHELLLINKPATH_CHILD_NAME);

      final @NotNull Path idePath = Paths.get(idePathStr);
      final @Nullable Path startMenuShellLinkPath = (startMenuShellLinkPathStr == null) ? null : Paths.get(startMenuShellLinkPathStr);
      final @Nullable Path taskbarShellLinkPath = (taskbarShellLinkPathStr == null) ? null : Paths.get(taskbarShellLinkPathStr);

      // keeping insertion order
      cacheEntries.remove(idePath);
      cacheEntries.put(idePath, new IdeEntry(appUserModelId, startMenuShellLinkPath, taskbarShellLinkPath));

      if (cacheEntries.size() > MAX_ENTRIES) {
        // removing oldest inserted entry
        cacheEntries.remove(cacheEntriesSet.iterator().next().getKey());
      }
    }

    return new WinShellIntegrationCache(cacheEntries);
  }

  static @NotNull WinShellIntegrationCache createEmpty() {
    return new WinShellIntegrationCache();
  }


  /**
   * @returns null if the entry does not exist
   */
  @Nullable IdeEntry pullThisIdeEntry() {
    return cacheEntries.remove(thisIdeHome);
  }

  /**
   * Inserts or updates this IDE's entry
   */
  void insertThisIdeEntry(@NotNull IdeEntry thisIdeEntry) {
    pullThisIdeEntry();
    cacheEntries.put(thisIdeHome, thisIdeEntry);
  }


  int getSize() {
    return cacheEntries.size();
  }

  void forEach(@NotNull BiConsumer<? super Path, ? super IdeEntry> consumer) {
    cacheEntries.forEach(consumer);
  }


  void saveToStorage(@NotNull Path storageDirPath) throws IOException {
    final var root = new Element("ides");
    for (final var entry : cacheEntries.entrySet()) {
      final var value = entry.getValue();

      if (StringUtilRt.isEmptyOrSpaces(value.appUserModelId)) {
        continue;
      }

      final var entrySerialized = new Element(IDE_ENTRY_NAME)
                                          .setAttribute(IDE_PATH_ATTRIBUTE_NAME, entry.getKey().normalize().toString());

      final var appUserModelIdSerialized = new Element(IDE_APPUSERMODELID_CHILD_NAME).setText(value.appUserModelId);
      entrySerialized.addContent(appUserModelIdSerialized);

      if (value.startMenuShellLinkPath != null) {
        final var startMenuShellLinkPathSerialized = new Element(IDE_STARTMENUSHELLLINKPATH_CHILD_NAME)
                                                             .setText(value.startMenuShellLinkPath.normalize().toString());
        entrySerialized.addContent(startMenuShellLinkPathSerialized);
      }

      if (value.taskbarShellLinkPath != null) {
        final var taskbarShellLinkPathSerialized = new Element(IDE_TASKBARSHELLLINKPATH_CHILD_NAME)
                                                           .setText(value.taskbarShellLinkPath.normalize().toString());
        entrySerialized.addContent(taskbarShellLinkPathSerialized);
      }

      root.addContent(entrySerialized);
    }

    try (final var stream = new FileOutputStream(storageDirPath.resolve(CACHE_FILE_NAME).toFile());
         final var channel = stream.getChannel()) {

      final var lock = channel.lock(); // will be released when the channel will be closed
      JDOMUtil.write(root, stream, "\n");
      stream.flush();
    }
  }


  private WinShellIntegrationCache() {
    cacheEntries = new LinkedHashMap<>();
  }

  private WinShellIntegrationCache(@NotNull LinkedHashMap<Path, IdeEntry> cacheEntries) {
    assert cacheEntries.size() <= MAX_ENTRIES;
    this.cacheEntries = cacheEntries;
  }


  private final @NotNull LinkedHashMap<Path, IdeEntry> cacheEntries;
  private final @NotNull Path thisIdeHome = Paths.get(PathManager.getHomePath());


  private static final @NotNull String CACHE_FILE_NAME = "cache.xml";

  private static final @NotNull String IDE_ENTRY_NAME = "ide";
  private static final @NotNull String IDE_PATH_ATTRIBUTE_NAME = "path";
  private static final @NotNull String IDE_APPUSERMODELID_CHILD_NAME = "AppUserModelID";
  private static final @NotNull String IDE_STARTMENUSHELLLINKPATH_CHILD_NAME = "startMenuShellLinkPath";
  private static final @NotNull String IDE_TASKBARSHELLLINKPATH_CHILD_NAME = "taskbarShellLinkPath";
}

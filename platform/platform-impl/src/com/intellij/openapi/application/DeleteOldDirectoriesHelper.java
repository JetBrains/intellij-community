/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.application;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DeleteOldDirectoriesHelper {
  public static void run() {
    DeleteOldDirectoriesSettings settings = new DeleteOldDirectoriesSettings();

    List<IdeDirectoriesInfo> dirsInfoList = gatherDirectories(settings);
    dirsInfoList = filterDirectoriesWithoutInstallation(dirsInfoList, settings.getApplicationInfoFilePath());

    if (!dirsInfoList.isEmpty()) {
      DeleteOldDirectoriesDialog dialog = new DeleteOldDirectoriesDialog(dirsInfoList);
      AppUIUtil.updateWindowIcon(dialog);
      dialog.setVisible(true);
    }
  }

  private static List<IdeDirectoriesInfo> filterDirectoriesWithoutInstallation(List<IdeDirectoriesInfo> dirsInfoList,
                                                                               String applicationInfoFilePath) {
    InstallationDetector installationDetector = new InstallationDetector(applicationInfoFilePath);
    return dirsInfoList.stream()
                       .filter(dirsInfo -> installationDetector.detectInstallation(dirsInfo) == null)
                       .collect(Collectors.toList());
  }

  private static List<IdeDirectoriesInfo> gatherDirectories(DeleteOldDirectoriesSettings settings) {
    final Path parent = settings.getUserHome();
    if (!parent.isAbsolute() || !Files.isDirectory(parent)) {
      return new ArrayList<>();
    }

    final String prefix = getDirectorySearchPrefix(settings.getSelector(), settings.isMac());
    if (prefix.isEmpty()) {
      return new ArrayList<>();
    }

    final DirectorySearchFilter filter = new DirectorySearchFilter(prefix, settings.getSelector(), settings.getIdeDirectories());
    final List<IdeDirectoriesInfo> dirsInfoList = settings.isMac() ? gatherDirectoriesMac(parent, filter) : gatherDirectoriesNonMac(parent, filter);
    Collections.sort(dirsInfoList);

    return dirsInfoList;
  }

  private static List<IdeDirectoriesInfo> gatherDirectoriesNonMac(Path parent, DirectorySearchFilter filter) {
    List<IdeDirectoriesInfo> dirsInfoList = new ArrayList<>();

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent, filter)) {
      for (Path dir : stream) {
        String selector = getSelectorFromDir(dir);
        String descriptor = dir.getFileName().toString();

        IdeDirectoriesInfo dirsInfo = new IdeDirectoriesInfo(selector, descriptor, dir);

        Path configDir = dir.resolve("config");
        if (!Files.isSymbolicLink(configDir) && Files.isDirectory(configDir)) {
          dirsInfo.addSubDirectory("Config", configDir);
        }

        Path systemDir = dir.resolve("system");
        if (!Files.isSymbolicLink(systemDir) && Files.isDirectory(systemDir)) {
          dirsInfo.addSubDirectory("System", systemDir);
        }

        dirsInfoList.add(dirsInfo);
      }
    } catch (IOException | DirectoryIteratorException ignored) {
    }

    return dirsInfoList;
  }

  private static List<IdeDirectoriesInfo> gatherDirectoriesMac(Path parent, DirectorySearchFilter filter) {
    Map<String, IdeDirectoriesInfo> dirsInfoMap = new HashMap<>();

    List<IdeDirectoriesInfo.DirectoryInfo> locations =
      Stream.of(new IdeDirectoriesInfo.DirectoryInfo("Config", parent.resolve("Library/Preferences")),
                new IdeDirectoriesInfo.DirectoryInfo("System", parent.resolve("Library/Caches")),
                new IdeDirectoriesInfo.DirectoryInfo("Plugins", parent.resolve("Library/Application Support")),
                new IdeDirectoriesInfo.DirectoryInfo("Log", parent.resolve("Library/Logs")))
            .collect(Collectors.toList());

    for (IdeDirectoriesInfo.DirectoryInfo location : locations) {
      Path directory = location.getDirectory();
      if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory)) {
        continue;
      }

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, filter)) {
        for (Path dir : stream) {
          String selector = getSelectorFromDir(dir);

          if (!dirsInfoMap.containsKey(selector)) {
            String descriptor = dir.getFileName().toString();
            dirsInfoMap.put(selector, new IdeDirectoriesInfo(selector, descriptor, null));
          }

          IdeDirectoriesInfo dirsInfo = dirsInfoMap.get(selector);
          dirsInfo.addSubDirectory(location.getDescriptor(), dir);
        }
      } catch (IOException | DirectoryIteratorException ignored) {
      }
    }

    return new ArrayList<>(dirsInfoMap.values());
  }

  /**
   * Filter that identifies old IDE directories.
   */
  private static class DirectorySearchFilter implements DirectoryStream.Filter<Path> {
    private final String desiredPrefix;
    private final String currentIdeSelector;
    private final List<DeleteOldDirectoriesSettings.PathWithTransformations> currentIdeDirectories;

    public DirectorySearchFilter(String desiredPrefix,
                                 String currentIdeSelector,
                                 List<DeleteOldDirectoriesSettings.PathWithTransformations> currentIdeDirectories) {
      this.desiredPrefix = desiredPrefix;
      this.currentIdeSelector = currentIdeSelector;
      this.currentIdeDirectories = currentIdeDirectories;
    }

    /**
     * Accepts path that is a directory, not a symbolic link, has the desired prefix,
     * has name that fits the expected pattern, does not equate to the current IDE selector,
     * and is not ancestor of current IDE directories.
     */
    @Override
    public boolean accept(Path entry) {
      try {
        if (Files.isSymbolicLink(entry) || !Files.isDirectory(entry)) {
          return false;
        }
        if (!StringUtil.startsWithIgnoreCase(entry.getFileName().toString(), desiredPrefix)) {
          return false;
        }
        String selector = getSelectorFromDir(entry);
        if (!isValidSelector(selector) || selector.equalsIgnoreCase(currentIdeSelector)) {
          return false;
        }
        if (checkIfAncestorOfIdeDirectories(entry, currentIdeDirectories)) {
          return false;
        }
        return true;
      } catch (Exception ex) {
        return false;
      }
    }
  }

  @NotNull
  private static String getSelectorFromDir(Path dir) {
    String dirName = dir.getFileName().toString();
    return dirName.startsWith(".") ? dirName.substring(1) : dirName;
  }

  /**
   * Pattern that defines a valid paths selector.
   *
   * Valid examples:
   *   name
   *   name3
   *   name3.2
   *   name34.2
   *   name34.23.45
   *
   * Invalid examples:
   *   3
   *   3.2
   *   name3..2
   *   name.3
   *   name.3.2
   *   name3.2.
   *   name/..
   *   name.com3.2
   */
  private static final Pattern SELECTOR_PATTERN = Pattern.compile("([a-zA-Z]+)(\\d+(\\.\\d+)*)?");
  private static final String PREVIEW_STRING = "Preview";

  public static boolean isValidSelector(String selector) {
    return SELECTOR_PATTERN.matcher(selector).matches();
  }

  /**
   * Prefix useful for searching old IDE directories, given a paths selector and whether the system is Mac OS.
   *
   * @param selector The paths selector, e.g. IdeaIC2018.1, IntelliJIdea2018.1, AndroidStudio3.2.
   * @param isMac Whether the operating system is Mac OS.
   * @return Appropriate prefix based on the selector and the OS, if the selector matches expected format,
   * otherwise empty string.
   */
  @NotNull
  public static String getDirectorySearchPrefix(String selector, boolean isMac) {
    Matcher matcher = SELECTOR_PATTERN.matcher(selector);
    if (matcher.matches()) {
      String name = matcher.group(1);

      // Android Studio: name ends with "Preview" for canary releases.  Remove it if present.
      if (name.endsWith(PREVIEW_STRING)) {
        name = name.substring(0, name.length() - PREVIEW_STRING.length());
      }

      return isMac ? name : "." + name;
    }
    return "";
  }

  private static boolean checkIfAncestorOfIdeDirectories(Path path,
                                                         List<DeleteOldDirectoriesSettings.PathWithTransformations> ideDirectories) {
    DeleteOldDirectoriesSettings.PathWithTransformations
      pathWithTransformations = new DeleteOldDirectoriesSettings.PathWithTransformations(path.toString());

    for (String transformedPath : pathWithTransformations) {
      if (checkIfAncestorOfPaths(transformedPath, ideDirectories)) {
        return true;
      }
    }

    return false;
  }

  private static boolean checkIfAncestorOfPaths(String ancestorPath,
                                                List<DeleteOldDirectoriesSettings.PathWithTransformations> pathsWithTransformations) {
    for (DeleteOldDirectoriesSettings.PathWithTransformations pathWithTransformations : pathsWithTransformations) {
      for (String path : pathWithTransformations) {
        if (StringUtil.startsWithIgnoreCase(path, ancestorPath)
          && (path.length() == ancestorPath.length() || path.charAt(ancestorPath.length()) == File.separatorChar)) {
          return true;
        }
      }
    }
    return false;
  }
}

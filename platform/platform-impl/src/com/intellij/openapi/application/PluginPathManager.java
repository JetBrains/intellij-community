/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.application;

import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.util.*;

/**
 * @author yole
 */
public class PluginPathManager {
  private PluginPathManager() {
  }

  private static class SubrepoHolder {
    public static List<File> subrepos = findSubrepos();

    private static List<File> findSubrepos() {
      List<File> result = new ArrayList<File>();
      File[] subdirs = new File(PathManager.getHomePath()).listFiles();
      if (subdirs == null) return result;
      Arrays.sort(subdirs, new Comparator<File>() {
        @Override
        public int compare(File file, File file2) {
          return FileUtil.compareFiles(file, file2);
        }
      });
      for (File subdir : subdirs) {
        if (new File(subdir, ".git").exists()) {
          File pluginsDir = new File(subdir, "plugins");
          if (pluginsDir.exists()) {
            result.add(pluginsDir);
          }
          else {
            result.add(subdir);
          }
        }
      }
      return result;
    }
  }

  public static File getPluginHome(String pluginName) {
    File subrepo = findSubrepo(pluginName);
    if (subrepo != null) {
      return subrepo;
    }
    return new File(PathManager.getHomePath(), "plugins/" + pluginName);
  }

  private static File findSubrepo(String pluginName) {
    for (File subrepo : SubrepoHolder.subrepos) {
      File candidate = new File(subrepo, pluginName);
      if (candidate.isDirectory()) {
        return candidate;
      }
    }
    return null;
  }

  public static String getPluginHomePath(String pluginName) {
    return getPluginHome(pluginName).getPath();
  }

  public static String getPluginHomePathRelative(String pluginName) {
    File subrepo = findSubrepo(pluginName);
    if (subrepo != null) {
      String homePath = FileUtil.toSystemIndependentName(PathManager.getHomePath());
      return "/" + FileUtil.getRelativePath(homePath, FileUtil.toSystemIndependentName(subrepo.getPath()), '/');
    }
    return "/plugins/" + pluginName;
  }
}

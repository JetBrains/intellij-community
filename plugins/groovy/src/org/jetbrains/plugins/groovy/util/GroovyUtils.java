/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.io.File;
import java.io.FilenameFilter;
import java.util.regex.Pattern;

/**
 * @author ilyas
 */
public abstract class GroovyUtils {
  /**
   * @param dir
   * @return true if current file is VCS auxiliary directory
   */
  public static boolean isVersionControlSysDir(final VirtualFile dir) {
    if (!dir.isDirectory()) {
      return false;
    }
    final String name = dir.getName().toLowerCase();
    return ".svn".equals(name) || "_svn".equals(name) ||
            ".cvs".equals(name) || "_cvs".equals(name);
  }

  /**
   * @param file
   * @return true if current file is true groovy file
   */

  public static boolean isGroovyFileOrDirectory(final @NotNull VirtualFile file) {
    return isGroovyFile(file) || file.isDirectory();
  }

  public static boolean isGroovyFile(VirtualFile file) {
    return GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension().equals(file.getExtension());
  }

  /**
   * @param module Module to get content root
   * @return VirtualFile corresponding to content root
   */
  @NotNull
  public static VirtualFile getModuleRoot(@NotNull final Module module) {
    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getSourceRoots();
    if (roots.length > 0) {
      return roots[0];
    }
    return (module.getModuleFile().getParent());
  }

  /**
   * @param module Module to get content root
   * @return VirtualFile array corresponding to content roots of current module
   */
  @NotNull
  public static VirtualFile[] getModuleRoots(@NotNull final Module module) {
    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getSourceRoots();
    return roots;
  }

  /**
   * @param module Module to get content root
   * @return VirtualFile corresponding to content root
   */
  @NotNull
  public static String[] getModuleRootUrls(@NotNull final Module module) {
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getSourceRoots();
    if (roots.length == 0) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    String[] urls = new String[roots.length];
    int i = 0;
    for (VirtualFile root : roots) {
      urls[i++] = root.getUrl();
    }
    return urls;
  }

  public static File[] getFilesInDirectoryByPattern(String dirPath, final String patternString) {
    File distDir = new File(dirPath);
    return distDir.listFiles(new FilenameFilter() {
      public boolean accept(File dir, String name) {
        Pattern pattern = Pattern.compile(patternString);
        return pattern.matcher(name).matches();
      }
    });
  }
}

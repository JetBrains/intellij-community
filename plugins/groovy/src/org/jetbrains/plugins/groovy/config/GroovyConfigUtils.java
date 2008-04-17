/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;

import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;
import java.util.jar.Attributes;
import java.util.List;
import java.io.File;
import java.io.IOException;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.util.GroovyUtils;

/**
 * @author ilyas
 */
public class GroovyConfigUtils {
  private static final String GROOVY_STARTER_FILE_NAME = "groovy";
  public static final String UNDEFINED_VERSION = "undefined";
  public static final String GROOVY_LIB_PATTERN = "groovy-\\d.*";
  public static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
  public static final String GROOVY_JAR_PATTERN = "groovy-all-\\d.*\\.jar";

  public static boolean isGroovySdkHome(final VirtualFile file) {
    final Ref<Boolean> result = Ref.create(false);
    processFilesUnderGDKRoot(file, new Processor<VirtualFile>() {
      public boolean process(final VirtualFile virtualFile) {
        result.set(true);
        return false;
      }
    });
    return result.get();
  }

  public static void processFilesUnderGDKRoot(VirtualFile file, final Processor<VirtualFile> processor) {
    if (file != null && file.isDirectory()) {
      final VirtualFile child = file.findChild("bin");

      if (child != null && child.isDirectory()) {
        for (VirtualFile grandChild : child.getChildren()) {
          if (GROOVY_STARTER_FILE_NAME.equals(grandChild.getNameWithoutExtension())) {
            if (!processor.process(grandChild)) return;
          }
        }
      }
    }
  }

  public static String getGroovyVersion(@NotNull String path) {
    if (!GroovyGrailsConfiguration.isGroovyConfigured(path)) return UNDEFINED_VERSION;
    String groovyJarVersion = getGroovyGrailsJarVersion(path + "/lib", "groovy-\\d.*\\.jar", MANIFEST_PATH);
    return groovyJarVersion != null ? groovyJarVersion : UNDEFINED_VERSION;
  }

  /**
   * Return value of Implementation-Version attribute in jar manifest
   * <p/>
   *
   * @param jarPath      directory containing jar file
   * @param jarRegex     filename pattern for jar file
   * @param manifestPath path to manifest file in jar file
   * @return value of Implementation-Version attribute, null if not found
   */
  public static String getGroovyGrailsJarVersion(String jarPath, final String jarRegex, String manifestPath) {
    try {
      File[] jars = GroovyUtils.getFilesInDirectoryByPattern(jarPath, jarRegex);
      if (jars.length != 1) {
        return null;
      }
      JarFile jarFile = new JarFile(jars[0]);
      JarEntry jarEntry = jarFile.getJarEntry(manifestPath);
      if (jarEntry == null) {
        return null;
      }
      Manifest manifest = new Manifest(jarFile.getInputStream(jarEntry));
      return manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
    }
    catch (Exception e) {
      return null;
    }
  }


  public static Library[] getGroovyLibraries(){
    LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable();
    List<Library> groovyLibs = ContainerUtil.findAll(table.getLibraries(), new Condition<Library>() {
      public boolean value(Library library) {
        return library.getName().matches(GROOVY_LIB_PATTERN);
      }
    });
    return groovyLibs.toArray(new Library[groovyLibs.size()]);

  }

  @NotNull
  @NonNls
  public static String getLibraryNameByVersion(final String selectedVersion) {
    return GroovyGrailsConfiguration.GROOVY_LIB_PREFIX + selectedVersion;
  }

  @NotNull
  static String getLibNameByVersion(String version) {
    return GroovyGrailsConfiguration.GROOVY_LIB_PREFIX + version;
  }

  @Nullable
  public static String getGroovyLibVersion(Library library) {
    VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
    for (VirtualFile file : files) {
      if (file.getName().matches(GROOVY_JAR_PATTERN)) {
        String path = file.getPresentableUrl();
        File realFile = new File(path);
        if (realFile.exists()) {
          try {
            JarFile jarFile = new JarFile(realFile);
            JarEntry jarEntry = jarFile.getJarEntry(MANIFEST_PATH);
            if (jarEntry == null) {
              continue;
            }
            Manifest manifest = new Manifest(jarFile.getInputStream(jarEntry));
            return manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
          } catch (IOException e) {
            continue;
          }
        }
      }
    }
    return null;
  }
}

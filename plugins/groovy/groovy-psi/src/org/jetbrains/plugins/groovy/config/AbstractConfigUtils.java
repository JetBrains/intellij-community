// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractConfigUtils {

  private static final Logger LOG = Logger.getInstance(AbstractConfigUtils.class);

  protected static final @NlsSafe String VERSION_GROUP_NAME = "version";

  private final Condition<Library> LIB_SEARCH_CONDITION = library -> isSDKLibrary(library);

  // Common entities
  public static final @NlsSafe String UNDEFINED_VERSION = "undefined";
  public static final @NlsSafe String MANIFEST_PATH = "META-INF/MANIFEST.MF";


  /**
   * Define, whether  given home is appropriate SDK home
   *
   */
  public abstract boolean isSDKHome(final VirtualFile file);

  public @NotNull @NlsSafe String getSDKVersion(@NlsSafe @NotNull String path) {
    String version = getSDKVersionOrNull(path);
    return version == null ? UNDEFINED_VERSION : version;
  }

  public abstract @NlsSafe @Nullable String getSDKVersionOrNull(@NlsSafe @NotNull String path);

  /**
   * Return value of Implementation-Version attribute in jar manifest
   * <p/>
   *
   * @param jarPath      directory containing jar file
   * @param jarRegex     filename pattern for jar file
   * @param manifestPath path to manifest file in jar file
   * @return value of Implementation-Version attribute, null if not found
   */
  public static @Nullable String getSDKJarVersion(String jarPath, final String jarRegex, String manifestPath) {
    return getSDKJarVersion(jarPath, Pattern.compile(jarRegex), manifestPath);
  }


  public static @Nullable String getSDKJarVersion(String jarPath, final Pattern jarPattern, String manifestPath) {
    return getSDKJarVersion(jarPath, jarPattern, manifestPath, VERSION_GROUP_NAME);
  }

  /**
   * Return value of Implementation-Version attribute in jar manifest
   * <p/>
   *
   * @param jarPath          directory containing jar file
   * @param jarPattern       filename pattern for jar file
   * @param manifestPath     path to manifest file in jar file
   * @param versionGroupName group name to get from matcher
   * @return value of Implementation-Version attribute, null if not found
   */
  public static @Nullable String getSDKJarVersion(String jarPath, final Pattern jarPattern, String manifestPath, String versionGroupName) {
    try {
      File[] jars = LibrariesUtil.getFilesInDirectoryByPattern(jarPath, jarPattern);
      if (jars.length == 0) {
        return null;
      }
      if (jars.length > 1) {
        Arrays.sort(jars);
      }
      try (JarFile jarFile = new JarFile(jars[0])) {
        JarEntry jarEntry = jarFile.getJarEntry(manifestPath);
        if (jarEntry == null) {
          return null;
        }
        Manifest manifest;
        try (InputStream inputStream = jarFile.getInputStream(jarEntry)) {
          manifest = new Manifest(inputStream);
        }
        final String version = manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        if (version != null) {
          return version;
        }

        final Matcher matcher = jarPattern.matcher(jars[0].getName());
        if (matcher.matches()) {
          try {
            return matcher.group(versionGroupName);
          }
          catch (IllegalArgumentException e) {
            LOG.error(e);
          }
        }
        return null;
      }
    }
    catch (Exception e) {
      LOG.debug(e);
      return null;
    }
  }

  public Library[] getProjectSDKLibraries(Project project) {
    if (project == null || project.isDisposed()) return Library.EMPTY_ARRAY;
    final LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
    final List<Library> all = ContainerUtil.findAll(table.getLibraries(), LIB_SEARCH_CONDITION);
    return all.toArray(Library.EMPTY_ARRAY);
  }

  public Library[] getAllSDKLibraries(@Nullable Project project) {
    return ArrayUtil.mergeArrays(getGlobalSDKLibraries(), getProjectSDKLibraries(project));
  }

  public Library[] getAllUsedSDKLibraries(Project project) {
    final List<Library> libraries = new ArrayList<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      libraries.addAll(Arrays.asList(getSDKLibrariesByModule(module)));
    }
    return libraries.toArray(Library.EMPTY_ARRAY);
  }

  public Library[] getGlobalSDKLibraries() {
    return LibrariesUtil.getGlobalLibraries(LIB_SEARCH_CONDITION);
  }

  public abstract boolean isSDKLibrary(Library library);

  public Library[] getSDKLibrariesByModule(final Module module) {
    return LibrariesUtil.getLibrariesByCondition(module, LIB_SEARCH_CONDITION);
  }
}

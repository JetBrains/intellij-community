// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.io.File;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * @author ilyas
 */
public abstract class GroovyConfigUtils extends AbstractConfigUtils {

  @NonNls public static final Pattern GROOVY_ALL_JAR_PATTERN = Pattern.compile("groovy-all(-minimal)?(-(\\d+(\\.\\d+)*))?(-indy|-alpha.*|-beta.*)?\\.jar");
  public static final int VERSION_GROUP_NUMBER = 3; // version will be in third group in GROOVY_ALL_JAR_PATTERN
  @NonNls public static final Pattern GROOVY_JAR_PATTERN = Pattern.compile("groovy(-(\\d+(\\.\\d+)*))?(-indy|-alpha.*|-beta.*)?\\.jar");

  public static final String NO_VERSION = "<no version>";
  public static final String GROOVY1_7 = "1.7";
  public static final String GROOVY1_8 = "1.8";
  public static final String GROOVY2_0 = "2.0";
  public static final String GROOVY2_1 = "2.1";
  public static final String GROOVY2_2 = "2.2";
  public static final String GROOVY2_2_2 = "2.2.2";
  public static final String GROOVY2_3 = "2.3";

  private static GroovyConfigUtils myGroovyConfigUtils;

  private GroovyConfigUtils() {
  }

  public static GroovyConfigUtils getInstance() {
    if (myGroovyConfigUtils == null) {
      myGroovyConfigUtils = new GroovyConfigUtils() {
        {
          STARTER_SCRIPT_FILE_NAME = "groovy";
        }};
    }
    return myGroovyConfigUtils;
  }

  @NotNull
  public static File[] getGroovyAllJars(@NotNull String path) {
    return LibrariesUtil.getFilesInDirectoryByPattern(path, GROOVY_ALL_JAR_PATTERN);
  }

  public static boolean matchesGroovyAll(@NotNull String name) {
    return GROOVY_ALL_JAR_PATTERN.matcher(name).matches() && !name.contains("src") && !name.contains("doc");
  }

  @Override
  @NotNull
  public String getSDKVersion(@NotNull final String path) {
    String groovyJarVersion = getSDKJarVersion(path + "/lib", GROOVY_JAR_PATTERN, MANIFEST_PATH, VERSION_GROUP_NUMBER);
    if (groovyJarVersion == null) {
      groovyJarVersion = getSDKJarVersion(path + "/lib", GROOVY_ALL_JAR_PATTERN, MANIFEST_PATH, VERSION_GROUP_NUMBER);
    }
    if (groovyJarVersion == null) {
      groovyJarVersion = getSDKJarVersion(path + "/embeddable", GROOVY_ALL_JAR_PATTERN, MANIFEST_PATH, VERSION_GROUP_NUMBER);
    }
    if (groovyJarVersion == null) {
      groovyJarVersion = getSDKJarVersion(path, GROOVY_ALL_JAR_PATTERN, MANIFEST_PATH, VERSION_GROUP_NUMBER);
    }
    return groovyJarVersion == null ? UNDEFINED_VERSION : groovyJarVersion;
  }

  @Override
  public boolean isSDKLibrary(Library library) {
    if (library == null) return false;
    return LibrariesUtil.getGroovyLibraryHome(library.getFiles(OrderRootType.CLASSES)) != null;
  }

  @Nullable
  public String getSDKVersion(@NotNull final Module module) {
    return CachedValuesManager.getManager(module.getProject()).getCachedValue(module, () -> {
      final String path = LibrariesUtil.getGroovyHomePath(module);
      return CachedValueProvider.Result.create(path == null ? null : getSDKVersion(path), ProjectRootManager.getInstance(module.getProject()));
    });
  }

  public boolean isVersionAtLeast(PsiElement psiElement, String version) {
    return isVersionAtLeast(psiElement, version, true);
  }

  public boolean isVersionAtLeast(PsiElement psiElement, String version, boolean unknownResult) {
    Module module = ModuleUtilCore.findModuleForPsiElement(psiElement);
    if (module == null) return unknownResult;
    final String sdkVersion = getSDKVersion(module);
    if (sdkVersion == null) return unknownResult;
    return sdkVersion.compareTo(version) >= 0;
  }

  @NotNull
  public String getSDKVersion(PsiElement psiElement) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(psiElement);
    if (module == null) {
      return NO_VERSION;
    }
    final String s = getSDKVersion(module);
    return s != null ? s : NO_VERSION;
  }


  @Override
  public boolean isSDKHome(VirtualFile file) {
    if (file != null && file.isDirectory()) {
      final String path = file.getPath();
      if (LibrariesUtil.getFilesInDirectoryByPattern(path + "/lib", GROOVY_JAR_PATTERN).length > 0 ||
          LibrariesUtil.getFilesInDirectoryByPattern(path + "/embeddable", GROOVY_ALL_JAR_PATTERN).length > 0 ||
          LibrariesUtil.getFilesInDirectoryByPattern(path, GROOVY_JAR_PATTERN).length > 0) {
        return true;
      }
    }
    return false;
  }


  @NotNull
  public String getSDKLibVersion(Library library) {
    return getSDKVersion(LibrariesUtil.getGroovyLibraryHome(library));
  }

  public Collection<String> getSDKVersions(Library[] libraries) {
    return ContainerUtil.map2List(libraries, library -> getSDKLibVersion(library));
  }
}

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

package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.util.Collection;

/**
 * @author ilyas
 */
public abstract class GroovyConfigUtils extends AbstractConfigUtils {
  @NonNls public static final String GROOVY_ALL_JAR_PATTERN = "groovy-all-(.*)\\.jar";

  private static GroovyConfigUtils myGroovyConfigUtils;
  @NonNls public static final String GROOVY_JAR_PATTERN_NOVERSION = "groovy\\.jar";
  @NonNls public static final String GROOVY_JAR_PATTERN = "groovy-(\\d.*)\\.jar";
  public static final String NO_VERSION = "<no version>";
  public static final String GROOVY1_7 = "1.7";
  public static final String GROOVY1_8 = "1.8";

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
  public String getSDKVersion(@NotNull final String path) {
    String groovyJarVersion = getSDKJarVersion(path + "/lib", GROOVY_JAR_PATTERN, MANIFEST_PATH);
    if (groovyJarVersion == null) {
      groovyJarVersion = getSDKJarVersion(path + "/lib", GROOVY_JAR_PATTERN_NOVERSION, MANIFEST_PATH);
    }
    if (groovyJarVersion == null) {
      groovyJarVersion = getSDKJarVersion(path + "/lib", GROOVY_ALL_JAR_PATTERN, MANIFEST_PATH);
    }
    if (groovyJarVersion == null) {
      groovyJarVersion = getSDKJarVersion(path + "/embeddable", GROOVY_ALL_JAR_PATTERN, MANIFEST_PATH);
    }
    return groovyJarVersion == null ? UNDEFINED_VERSION : groovyJarVersion;
  }

  public boolean isSDKLibrary(Library library) {
    if (library == null) return false;
    return LibrariesUtil.getGroovyLibraryHome(library.getFiles(OrderRootType.CLASSES)) != null;
  }

  @Nullable
  public String getSDKVersion(@NotNull final Module module) {
    return CachedValuesManager.getManager(module.getProject()).getCachedValue(module, new CachedValueProvider<String>() {
      @Override
      public Result<String> compute() {
        final String path = LibrariesUtil.getGroovyHomePath(module);
        if (path == null) return null;
        return Result.create(getSDKVersion(path), ProjectRootManager.getInstance(module.getProject()));
      }
    });
  }

  public boolean isVersionAtLeast(PsiElement psiElement, String version) {
    return isVersionAtLeast(psiElement, version, true);
  }
  public boolean isVersionAtLeast(PsiElement psiElement, String version, boolean unknownResult) {
    Module module = ModuleUtil.findModuleForPsiElement(psiElement);
    if (module == null) return unknownResult;
    final String sdkVersion = getSDKVersion(module);
    if (sdkVersion == null) return unknownResult;
    return sdkVersion.compareTo(version) >= 0;
  }

  @NotNull
  public String getSDKVersion(PsiElement psiElement) {
    final Module module = ModuleUtil.findModuleForPsiElement(psiElement);
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
      if (GroovyUtils.getFilesInDirectoryByPattern(path + "/lib", GROOVY_JAR_PATTERN).length > 0) {
        return true;
      }
      if (GroovyUtils.getFilesInDirectoryByPattern(path + "/lib", GROOVY_JAR_PATTERN_NOVERSION).length > 0) {
        return true;
      }
      if (GroovyUtils.getFilesInDirectoryByPattern(path + "/embeddable", GROOVY_ALL_JAR_PATTERN).length > 0) {
        return true;
      }
    }
    return false;
  }

  public boolean tryToSetUpGroovyFacetOntheFly(final Module module) {
    final Project project = module.getProject();
    final Library[] libraries = getAllSDKLibraries(project);
    if (libraries.length > 0) {
      final Library library = libraries[0];
      int result = Messages
        .showOkCancelDialog(GroovyBundle.message("groovy.like.library.found.text", module.getName(), library.getName(), getSDKLibVersion(library)),
                            GroovyBundle.message("groovy.like.library.found"), GroovyIcons.GROOVY_ICON_32x32);
      if (result == 0) {
        AccessToken accessToken = WriteAction.start();

        try {
          ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
          LibraryOrderEntry entry = model.addLibraryEntry(libraries[0]);
          LibrariesUtil.placeEntryToCorrectPlace(model, entry);
          model.commit();
          return true;
        }
        finally {
          accessToken.finish();
        }
      }
    }
    return false;
  }

  @NotNull
  public String getSDKLibVersion(Library library) {
    return getSDKVersion(LibrariesUtil.getGroovyLibraryHome(library));
  }

  public Collection<String> getSDKVersions(final Project project) {
    return ContainerUtil.map2List(getAllSDKLibraries(project), new Function<Library, String>() {
      public String fun(Library library) {
        return getSDKLibVersion(library);
      }
    });
  }
}

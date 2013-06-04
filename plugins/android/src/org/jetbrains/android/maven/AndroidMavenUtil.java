/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.maven;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.Collection;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidMavenUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.maven.AndroidMavenUtil");

  @NonNls public static final String APKSOURCES_DEPENDENCY_TYPE = "apksources";
  @NonNls public static final String APKLIB_DEPENDENCY_AND_PACKAGING_TYPE = "apklib";
  @NonNls public static final String SO_PACKAGING_AND_DEPENDENCY_TYPE = "so";
  @NonNls public static final String APK_PACKAGING_TYPE = "apk";

  @NonNls public static final String APK_LIB_ARTIFACT_SOURCE_ROOT = "src";
  @NonNls public static final String APK_LIB_ARTIFACT_RES_DIR = "res";
  public static final String APK_LIB_ARTIFACT_NATIVE_LIBS_DIR = "libs";
  public static final String APK_LIB_ARTIFACT_MANIFEST_FILE = "AndroidManifest.xml";
  @NonNls private static final String APKLIB_MODULE_PREFIX = "~apklib-";
  @NonNls private static final String GEN_EXTERNAL_APKLIBS_DIRNAME = "gen-external-apklibs";

  private AndroidMavenUtil() {
  }

  @Nullable
  public static String computePathForGenExternalApklibsDir(@NotNull MavenId mavenId,
                                                           @NotNull MavenProject project,
                                                           @NotNull Collection<MavenProject> allProjects) {
    String path = null;
    boolean resultUnderApp = false;

    for (MavenProject p : allProjects) {
      List<MavenArtifact> dependencies = p.findDependencies(mavenId);

      if (dependencies.size() == 0) {
        dependencies = p.findDependencies(mavenId.getGroupId(), mavenId.getArtifactId());
      }

      if (dependencies.size() > 0 && containsCompileDependency(dependencies)) {
        final VirtualFile projectDir = p.getDirectoryFile();
        final boolean app = APK_PACKAGING_TYPE.equals(p.getPackaging());
        if (path == null || !resultUnderApp && app) {
          path = projectDir.getPath() + '/' + GEN_EXTERNAL_APKLIBS_DIRNAME;
          resultUnderApp = app;
        }
      }
    }
    
    if (path == null) {
      path = project.getDirectoryFile().getPath() + '/' + GEN_EXTERNAL_APKLIBS_DIRNAME;
    }
    return path;
  }

  private static boolean containsCompileDependency(Collection<MavenArtifact> dependencies) {
    for (MavenArtifact dependency : dependencies) {
      if (MavenConstants.SCOPE_COMPILE.equals(dependency.getScope())) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static String getModuleNameForExtApklibArtifact(MavenId mavenId) {
    return APKLIB_MODULE_PREFIX + getMavenIdStringForFileName(mavenId);
  }
  
  @Nullable
  public static String getMavenIdStringByExtApklibModule(@NotNull Module module) {
    final String moduleName = module.getName();
    
    if (!moduleName.startsWith(APKLIB_MODULE_PREFIX)) {
      return null;
    }
    
    return moduleName.substring(APKLIB_MODULE_PREFIX.length());
  }

  public static boolean isExtApklibModule(@NotNull Module module) {
    return module.getName().startsWith(APKLIB_MODULE_PREFIX);
  }

  @NotNull
  public static String getMavenIdStringForFileName(@NotNull MavenId mavenId) {
    final String artifactId = mavenId.getKey().replace(':', '_');
    return artifactId != null ? artifactId : "null";
  }

  public static boolean isMavenizedModule(@NotNull Module module) {
    AndroidMavenProvider mavenProxy = getMavenProvider();
    return mavenProxy != null && mavenProxy.isMavenizedModule(module);
  }

  @Nullable
  public static AndroidMavenProvider getMavenProvider() {
    return ArrayUtil.getFirstElement(AndroidMavenProvider.EP_NAME.getExtensions());
  }
}

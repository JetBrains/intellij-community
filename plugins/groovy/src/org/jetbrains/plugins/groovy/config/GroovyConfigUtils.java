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

import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

/**
 * @author ilyas
 */
public abstract class GroovyConfigUtils extends AbstractConfigUtils {
  @NonNls private static final String GROOVY_ALL_JAR_PATTERN = "groovy-all-(.*)\\.jar";

  @NonNls private static final String DGM_CLASS_PATH = "org/codehaus/groovy/runtime/DefaultGroovyMethods.class";
  @NonNls private static final String CLOSURE_CLASS_PATH = "groovy/lang/Closure.class";

  private static GroovyConfigUtils myGroovyConfigUtils;
  @NonNls private static final String GROOVY_JAR_PATTERN = "groovy-(\\d.*)\\.jar";

  private GroovyConfigUtils() {
  }

  public static GroovyConfigUtils getInstance() {
    if (myGroovyConfigUtils == null) {
      myGroovyConfigUtils = new GroovyConfigUtils() {
        {
          SDK_LIB_PREFIX = "groovy-";
          KEY_CLASSES = new String[]{DGM_CLASS_PATH, CLOSURE_CLASS_PATH};
          ERR_MESSAGE = GroovyBundle.message("invalid.groovy.sdk.path.message");
          STARTER_SCRIPT_FILE_NAME = "groovy";
        }};
    }
    return myGroovyConfigUtils;
  }

  @NotNull
  public String getSDKVersion(@NotNull final String path) {
    String groovyJarVersion = getSDKJarVersion(path + "/lib", GROOVY_JAR_PATTERN, MANIFEST_PATH);
    if (groovyJarVersion != null) {
      return groovyJarVersion;
    }

    groovyJarVersion = getSDKJarVersion(path + "/lib", GROOVY_ALL_JAR_PATTERN, MANIFEST_PATH);
    if (groovyJarVersion != null) {
      return groovyJarVersion;
    }
    
    return UNDEFINED_VERSION;
  }

  public boolean isSDKLibrary(Library library) {
    if (library == null) return false;
    return isGroovyLibrary(library.getFiles(OrderRootType.CLASSES));
  }

  public static boolean isGroovyLibrary(VirtualFile[] classFiles) {
    for (VirtualFile file : classFiles) {
      if (isGroovyAllJar(file.getName())) {
        return true;
      }
    }
    return false;
  }

  public String getSDKVersion(@NotNull Library library) {
    return getSDKVersion(LibrariesUtil.getGroovyLibraryHome(library));
  }

  public static boolean isGroovyAllJar(@NonNls final String name) {
    return name.matches(GROOVY_ALL_JAR_PATTERN) || name.matches(GROOVY_JAR_PATTERN);
  }

  @NotNull
  public String getSDKInstallPath(Module module) {
    if (module == null) return "";
    Library[] libraries = getSDKLibrariesByModule(module);
    if (libraries.length == 0) return "";
    Library library = libraries[0];
    return LibrariesUtil.getGroovyLibraryHome(library);
  }

  @Override
  public boolean isSDKHome(VirtualFile file) {
    if (file != null && file.isDirectory()) {
      final String path = file.getPath();
      if (GroovyUtils.getFilesInDirectoryByPattern(path + "/lib", GROOVY_JAR_PATTERN).length > 0) {
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
      final Ref<Boolean> ref = new Ref<Boolean>();
      ref.set(false);
      if (result == 0) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
            LibraryOrderEntry entry = model.addLibraryEntry(libraries[0]);
            LibrariesUtil.placeEntryToCorrectPlace(model, entry);
            model.commit();
            ref.set(true);
            final FacetManager manager = FacetManager.getInstance(module);
            final GroovyFacetType type = GroovyFacetType.getInstance();
            final FacetTypeId<GroovyFacet> id = type.getId();
            final GroovyFacet facetByType = manager.getFacetByType(id);
            if (facetByType == null) {
              manager.addFacet(type, type.getPresentableName(), null);
            }
          }
        });
      }
      return ref.get().booleanValue();
    }
    return false;
  }
}

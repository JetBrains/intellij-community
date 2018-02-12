// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.io.File;

import static org.jetbrains.plugins.groovy.bundled.BundledGroovy.getBundledGroovyFile;

public class GroovyFacetUtil {
  public static final String PLUGIN_MODULE_ID = "PLUGIN_MODULE";

  public static boolean tryToSetUpGroovyFacetOnTheFly(final Module module) {
    final Project project = module.getProject();
    GroovyConfigUtils utils = GroovyConfigUtils.getInstance();
    final Library[] libraries = utils.getAllSDKLibraries(project);
    if (libraries.length > 0) {
      final Library library = libraries[0];
      int result = Messages
        .showOkCancelDialog(
          GroovyBundle.message("groovy.like.library.found.text", module.getName(), library.getName(), utils.getSDKLibVersion(library)),
          GroovyBundle.message("groovy.like.library.found"), JetgroovyIcons.Groovy.Groovy_32x32);
      if (result == Messages.OK) {
        WriteAction.run(() -> {
          ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
          LibraryOrderEntry entry = model.addLibraryEntry(libraries[0]);
          LibrariesUtil.placeEntryToCorrectPlace(model, entry);
          model.commit();
        });
        return true;
      }
    }
    return false;
  }

  public static boolean isSuitableModule(Module module) {
    if (module == null) return false;
    return isAcceptableModuleType(ModuleType.get(module));
  }

  public static boolean isAcceptableModuleType(ModuleType type) {
    return type instanceof JavaModuleType || PLUGIN_MODULE_ID.equals(type.getId()) || "ANDROID_MODULE".equals(type.getId());
  }

  @Deprecated
  @NotNull
  public static File getBundledGroovyJar() {
    return getBundledGroovyFile();
  }
}

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

package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyFileTypeLoader;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry.Krasilschikov
 */

public class GroovyCompiler extends GroovyCompilerBase {
  private static final String GROOVY_COMPILER = "groovy compiler";

  public GroovyCompiler(Project project) {
    super(project);
  }

  @NotNull
  public String getDescription() {
    return GROOVY_COMPILER;
  }

  @Override
  protected void compileFiles(CompileContext compileContext, Module module, List<VirtualFile> toCompile, VirtualFile outputDir, OutputSink sink) {
    runGroovycCompiler(compileContext, module, toCompile, false, outputDir, sink);
  }

  public boolean validateConfiguration(CompileScope compileScope) {
    VirtualFile[] files = compileScope.getFiles(GroovyFileType.GROOVY_FILE_TYPE, true);
    if (files.length == 0) return true;

    final Set<String> scriptExtensions = new THashSet<String>(GroovyFileTypeLoader.getCustomGroovyScriptExtensions());

    Set<Module> modules = new HashSet<Module>();
    for (VirtualFile file : files) {
      if (scriptExtensions.contains(file.getExtension())) {
        continue;
      }

      ProjectRootManager rootManager = ProjectRootManager.getInstance(myProject);
      Module module = rootManager.getFileIndex().getModuleForFile(file);
      if (module != null) {
        modules.add(module);
      }
    }

    Set<Module> nojdkModules = new HashSet<Module>();
    for (Module module : modules) {
      if(!(module.getModuleType() instanceof JavaModuleType)) return true;
      final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk == null || !(sdk.getSdkType() instanceof JavaSdkType)) {
        nojdkModules.add(module);
        continue;
      }

      if (!LibrariesUtil.hasGroovySdk(module)) {
        if (!GroovyConfigUtils.getInstance().tryToSetUpGroovyFacetOntheFly(module)) {
          Messages.showErrorDialog(myProject, GroovyBundle.message("cannot.compile.groovy.files.no.facet", module.getName()),
                                   GroovyBundle.message("cannot.compile"));
          ModulesConfigurator.showDialog(module.getProject(), module.getName(), ClasspathEditor.NAME, false);
          return false;
        }
      }
    }

    if (!nojdkModules.isEmpty()) {
      final Module[] noJdkArray = nojdkModules.toArray(new Module[nojdkModules.size()]);
      if (noJdkArray.length == 1) {
        Messages.showErrorDialog(myProject, GroovyBundle.message("cannot.compile.groovy.files.no.sdk", noJdkArray[0].getName()),
                                 GroovyBundle.message("cannot.compile"));
      }
      else {
        StringBuffer modulesList = new StringBuffer();
        for (int i = 0; i < noJdkArray.length; i++) {
          if (i > 0) modulesList.append(", ");
          modulesList.append(noJdkArray[i].getName());
        }
        Messages.showErrorDialog(myProject, GroovyBundle.message("cannot.compile.groovy.files.no.sdk.mult", modulesList.toString()),
                                 GroovyBundle.message("cannot.compile"));
      }
      return false;
    }

    return true;
  }

}

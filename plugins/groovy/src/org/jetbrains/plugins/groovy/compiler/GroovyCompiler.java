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

package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyFileTypeLoader;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry.Krasilschikov
 */

public class GroovyCompiler extends GroovyCompilerBase {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.compiler.GroovyCompiler");
  private static final String AST_TRANSFORM_FILE_NAME = "org.codehaus.groovy.transform.ASTTransformation";

  public GroovyCompiler(Project project) {
    super(project);
  }

  @NotNull
  public String getDescription() {
    return "groovy compiler";
  }

  @Override
  protected void compileFiles(final CompileContext context, final Module module, List<VirtualFile> toCompile, OutputSink sink, boolean tests) {
    context.getProgressIndicator().checkCanceled();
    context.getProgressIndicator().setText("Starting Groovy compiler...");

    runGroovycCompiler(context, module, toCompile, false, getMainOutput(context, module, tests), sink, tests);
  }

  public boolean validateConfiguration(CompileScope compileScope) {
    VirtualFile[] files = compileScope.getFiles(GroovyFileType.GROOVY_FILE_TYPE, true);
    if (files.length == 0) return true;

    final Set<String> scriptExtensions = GroovyFileTypeLoader.getCustomGroovyScriptExtensions();

    final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    Set<Module> modules = new HashSet<Module>();
    for (VirtualFile file : files) {
      if (scriptExtensions.contains(file.getExtension()) ||
          compilerManager.isExcludedFromCompilation(file) ||
          CompilerConfiguration.getInstance(myProject).isResourceFile(file)) {
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
      if(!GroovyUtils.isAcceptableModuleType(ModuleType.get(module))) continue;
      final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk == null || !(sdk.getSdkType() instanceof JavaSdkType)) {
        nojdkModules.add(module);
        continue;
      }

      if (!LibrariesUtil.hasGroovySdk(module)) {
        if (!GroovyConfigUtils.getInstance().tryToSetUpGroovyFacetOnTheFly(module)) {
          Messages.showErrorDialog(myProject, GroovyBundle.message("cannot.compile.groovy.files.no.facet", module.getName()),
                                   GroovyBundle.message("cannot.compile"));
          ModulesConfigurator.showDialog(module.getProject(), module.getName(), ClasspathEditor.NAME);
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
        StringBuilder modulesList = new StringBuilder();
        for (int i = 0; i < noJdkArray.length; i++) {
          if (i > 0) modulesList.append(", ");
          modulesList.append(noJdkArray[i].getName());
        }
        Messages.showErrorDialog(myProject, GroovyBundle.message("cannot.compile.groovy.files.no.sdk.mult", modulesList.toString()),
                                 GroovyBundle.message("cannot.compile"));
      }
      return false;
    }

    final GroovyCompilerConfiguration configuration = GroovyCompilerConfiguration.getInstance(myProject);
    if (!configuration.transformsOk && needTransformCopying(compileScope)) {
      final int result = Messages.showYesNoDialog(myProject,
                                                  "You seem to have global Groovy AST transformations defined in your project,\n" +
                                                  "but they won't be applied to your code because they are not marked as compiler resources.\n" +
                                                  "Do you want to add them to compiler resource list?\n" +
                                                  "(you can do it yourself later in Settings | Compiler | Resource patterns)",
                                                  "AST Transformations Found",
                                                  JetgroovyIcons.Groovy.Groovy_32x32);
      if (result == 0) {
        CompilerConfiguration.getInstance(myProject).addResourceFilePattern(AST_TRANSFORM_FILE_NAME);
      } else {
        configuration.transformsOk = true;
      }
    }

    return true;
  }

  private boolean needTransformCopying(CompileScope compileScope) {
    final CompilerConfiguration configuration = CompilerConfiguration.getInstance(myProject);
    final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    for (VirtualFile file : FilenameIndex.getVirtualFilesByName(myProject, AST_TRANSFORM_FILE_NAME, GlobalSearchScope.projectScope(myProject))) {
      if (compileScope.belongs(file.getUrl()) && index.isInSource(file) && !configuration.isResourceFile(file)) {
        return true;
      }
    }
    return false;
  }

}

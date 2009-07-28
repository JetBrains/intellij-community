/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.compiler.generator;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.IntermediateOutputCompiler;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.compiler.GroovyCompilerBase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class NewGroovyToJavaGenerator extends GroovyCompilerBase implements IntermediateOutputCompiler {
  public NewGroovyToJavaGenerator(Project project) {
    super(project);
  }

  @Override
  public ExitStatus compile(CompileContext compileContext, VirtualFile[] virtualFiles) {
    boolean hasTests = false;
    for (final VirtualFile item : virtualFiles) {
      if (ProjectRootManager.getInstance(myProject).getFileIndex().isInTestSourceContent(item)) {
        hasTests = true;
        break;
      }
    }

    List<VirtualFile> total = new ArrayList<VirtualFile>();
    total.addAll(Arrays.asList(virtualFiles));

    for (final VirtualFile javaFile : compileContext.getCompileScope().getFiles(StdFileTypes.JAVA, !hasTests)) {
      if (isCompilableJavaFile(javaFile, compileContext)) {
        total.add(javaFile);
      }
    }

    return super.compile(compileContext, total.toArray(new VirtualFile[total.size()]));
  }

  @Override
  protected void copyFiles(CompileContext compileContext,
                           Set<OutputItem> successfullyCompiled,
                           Set<VirtualFile> toRecompileCollector,
                           List<VirtualFile> toCopy,
                           CompilerConfiguration configuration) {
  }

  @Override
  protected void compileFiles(CompileContext compileContext,
                              Set<OutputItem> successfullyCompiled,
                              Set<VirtualFile> toRecompileCollector,
                              Module module,
                              List<VirtualFile> toCompile) {
    if (!isMixedProject(toCompile)) {
      return;
    }

    runGroovycCompiler(compileContext, successfullyCompiled, toRecompileCollector, module, toCompile, true);
  }

  private static boolean isMixedProject(List<VirtualFile> toCompile) {
    final VirtualFile anyJavaFile = ContainerUtil.find(toCompile, new Condition<VirtualFile>() {
      public boolean value(VirtualFile file) {
        return file.getFileType() == StdFileTypes.JAVA;
      }
    });
    final VirtualFile anyGroovyFile = ContainerUtil.find(toCompile, new Condition<VirtualFile>() {
      public boolean value(VirtualFile file) {
        return file.getFileType() == GroovyFileType.GROOVY_FILE_TYPE;
      }
    });
    return anyJavaFile != null && anyGroovyFile != null;
  }

  @NotNull
  public String getDescription() {
    return "Groovy to java source code generator";
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  private static boolean isCompilableJavaFile(VirtualFile file, CompileContext context) {
    final Module module = context.getModuleByFile(file);
    if (module == null) {
      return false;
    }

    final VirtualFile output = context.getModuleOutputDirectory(module);
    if (output != null && VfsUtil.isAncestor(output, file, true)) {
      return false;
    }
    final VirtualFile testOutput = context.getModuleOutputDirectoryForTests(module);
    if (testOutput != null && VfsUtil.isAncestor(testOutput, file, true)) {
      return false;
    }

    return true;
  }
}
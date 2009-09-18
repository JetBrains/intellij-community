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

import com.intellij.compiler.impl.TranslatingCompilerFilesMonitor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.compiler.GroovyCompilerBase;
import org.jetbrains.plugins.groovy.compiler.GroovyCompilerConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class GroovycStubGenerator extends GroovyCompilerBase {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.compiler.generator.GroovycStubGenerator");

  public GroovycStubGenerator(Project project) {
    super(project);
  }

  @Override
  public void compile(CompileContext compileContext, VirtualFile[] virtualFiles, OutputSink sink) {
    final CompileScope scope = compileContext.getCompileScope();
    if (scope.getFiles(StdFileTypes.JAVA, true).length == 0) {
      return;
    }

    boolean hasGroovy = false;
    final ExcludedEntriesConfiguration excluded = GroovyCompilerConfiguration.getInstance(myProject).getState().excludeFromStubGeneration;
    List<VirtualFile> total = new ArrayList<VirtualFile>();
    for (final VirtualFile virtualFile : virtualFiles) {
      if (!excluded.isExcluded(virtualFile)) {
        total.add(virtualFile);
        if (virtualFile.getFileType() == GroovyFileType.GROOVY_FILE_TYPE) {
          hasGroovy = true;
        }
      }
    }

    if (!hasGroovy) {
      return;
    }

    super.compile(compileContext, total.toArray(new VirtualFile[total.size()]), sink);
  }

  @Override
  protected void compileFiles(CompileContext compileContext, Module module,
                              final List<VirtualFile> toCompile, VirtualFile outputDir, OutputSink sink) {
    boolean hasGroovy = false;
    boolean hasJava = false;
    for (final VirtualFile file : toCompile) {
      if (file.getFileType() == StdFileTypes.JAVA) {
        hasJava = true;
      }
      if (file.getFileType() == GroovyFileType.GROOVY_FILE_TYPE) {
        hasGroovy = true;
      }
    }

    if (!hasGroovy) {
      return;
    }

    final String rootPath = CompilerPaths.getGeneratedDataDirectory(myProject) + "/groovyStubs/";
    final File outDir = new File(rootPath + myProject.getLocationHash() + "/" + module.getName() + "/");
    outDir.mkdirs();

    if (!hasJava) {
      //always pass groovyc stub generator at least 1 java file, or it won't generate stubs
      toCompile.add(createMockJavaFile(rootPath));
    }

    final VirtualFile tempOutput = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outDir);
    assert tempOutput != null;
    cleanDirectory(tempOutput);

    ((CompileContextEx)compileContext).assignModule(tempOutput, module, false);

    runGroovycCompiler(compileContext, module, toCompile, true, tempOutput, sink);
  }

  private VirtualFile createMockJavaFile(final String rootPath) {
    return new WriteCommandAction<VirtualFile>(myProject) {
      protected void run(Result<VirtualFile> result) throws Throwable {
        final VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(rootPath));
        final String sampleClassName = "$$$VeryEmptyJavaClass$$$";
        assert root != null;
        final VirtualFile sampleClass = root.findOrCreateChildData(this, sampleClassName + ".java");
        VfsUtil.saveText(sampleClass, "public class " + sampleClassName + " {}");
        result.setResult(sampleClass);
      }
    }.execute().getResultObject();
  }

  private void cleanDirectory(final VirtualFile dir) {
    new WriteCommandAction(myProject) {
      protected void run(Result result) throws Throwable {
        deleteChildrenRecursively(dir);
      }

      private void deleteChildrenRecursively(final VirtualFile dir) throws IOException {
        for (final VirtualFile child : dir.getChildren()) {
          if (child.isDirectory()) {
            deleteChildrenRecursively(dir);
          }
          TranslatingCompilerFilesMonitor.removeSourceInfo(child);
          child.delete(this);
        }
      }
    }.execute();
  }

  @Override
  public boolean isCompilableFile(VirtualFile file, CompileContext context) {
    return super.isCompilableFile(file, context) || StdFileTypes.JAVA == file.getFileType();
  }

  @NotNull
  public String getDescription() {
    return "Groovy to java source code generator";
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

}
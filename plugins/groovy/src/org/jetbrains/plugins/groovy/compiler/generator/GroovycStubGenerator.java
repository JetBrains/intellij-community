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

package org.jetbrains.plugins.groovy.compiler.generator;

import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.FileSetCompileScope;
import com.intellij.compiler.impl.TranslatingCompilerFilesMonitor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Chunk;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.compiler.GroovyCompilerBase;
import org.jetbrains.plugins.groovy.compiler.GroovyCompilerConfiguration;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.refactoring.convertToJava.GroovyToJavaGenerator;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author peter
 */
public class GroovycStubGenerator extends GroovyCompilerBase {
  private static Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.compiler.generator.GroovycStubGenerator");

  public static final String GROOVY_STUBS = "groovyStubs";

  public GroovycStubGenerator(Project project) {
    super(project);
  }

  @Override
  public void compile(CompileContext compileContext, Chunk<Module> moduleChunk, VirtualFile[] virtualFiles, OutputSink sink) {
    boolean hasJava = false;

    final ExcludedEntriesConfiguration excluded = GroovyCompilerConfiguration.getExcludeConfiguration(myProject);

    List<VirtualFile> total = new ArrayList<VirtualFile>();
    for (final VirtualFile virtualFile : virtualFiles) {
      final FileType fileType = virtualFile.getFileType();
      if (fileType == StdFileTypes.JAVA) {
        hasJava = true;
      }

      if (!excluded.isExcluded(virtualFile)) {
        if (fileType == GroovyFileType.GROOVY_FILE_TYPE && GroovyNamesUtil.isIdentifier(virtualFile.getNameWithoutExtension())) {
          total.add(virtualFile);
        }
      }
    }

    if (!hasJava) {
      return;
    }

    if (total.isEmpty()) {
      return;
    }

    //long l = System.currentTimeMillis();
    super.compile(compileContext, moduleChunk, VfsUtil.toVirtualFileArray(total), sink);
    //System.out.println("Stub generation took " + (System.currentTimeMillis() - l));
  }

  @Override
  public boolean isCompilableFile(VirtualFile file, CompileContext context) {
    return super.isCompilableFile(file, context) || StdFileTypes.JAVA.equals(file.getFileType());
  }

  @Override
  protected void compileFiles(CompileContext compileContext,
                              Module module,
                              final List<VirtualFile> toCompile,
                              OutputSink sink,
                              boolean tests) {
    final File outDir = getStubOutput(module, tests);
    outDir.mkdirs();

    final VirtualFile tempOutput = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outDir);
    assert tempOutput != null;
    cleanDirectory(tempOutput);

    ((CompileContextEx)compileContext).assignModule(tempOutput, module, tests, this);

    if (GroovyCompilerConfiguration.getInstance(myProject).isUseGroovycStubs()) {
      runGroovycCompiler(compileContext, module, toCompile, true, tempOutput, sink, tests);
    }
    else {
      ProgressIndicator indicator = compileContext.getProgressIndicator();
      indicator.pushState();

      try {
        final GroovyToJavaGenerator generator = new GroovyToJavaGenerator(myProject, new HashSet<VirtualFile>(toCompile), false);
        for (int i = 0; i < toCompile.size(); i++) {
          indicator.setFraction((double)i / toCompile.size());

          final Collection<VirtualFile> stubFiles = generateItems(generator, toCompile.get(i), tempOutput, compileContext, myProject);
          ((CompileContextEx)compileContext).addScope(new FileSetCompileScope(stubFiles, new Module[]{module}));
        }
      }
      finally {
        indicator.popState();
      }
    }
  }

  private static File getStubOutput(Module module, boolean tests) {
    final Project project = module.getProject();
    final String rootPath = CompilerPaths.getGeneratedDataDirectory(project).getPath() + "/" + GROOVY_STUBS + "/";
    return new File(rootPath + project.getLocationHash() + "/" + module.getName() + "/" + (tests ? "tests" : "production") + "/");
  }

  @Nullable
  public static PsiClass findClassByStub(Project project, VirtualFile stubFile) {
    final String[] components = StringUtil.trimEnd(stubFile.getPath(), ".java").split("[\\\\/]");
    final int stubs = Arrays.asList(components).indexOf(GROOVY_STUBS);
    if (stubs < 0 || stubs >= components.length - 4) return null;

    final String moduleName = components[stubs + 2];
    final Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
    if (module == null) return null;

    final String fqn = StringUtil.join(Arrays.asList(components).subList(stubs + 4, components.length), ".");
    return JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.moduleScope(module));
  }

  private void cleanDirectory(final VirtualFile dir) {
    new WriteCommandAction(myProject) {
      protected void run(Result result) throws Throwable {
        deleteChildrenRecursively(dir);
      }

      private void deleteChildrenRecursively(final VirtualFile dir) throws IOException {
        for (final VirtualFile child : dir.getChildren()) {
          if (child.isDirectory()) {
            deleteChildrenRecursively(child);
          }
          TranslatingCompilerFilesMonitor.removeSourceInfo(child);
          try {
            child.delete(this);
          }
          catch (IOException ignored) {
            //may be a leaked handle from some non-completely terminated compiler process, or compiler caches, or something else
            //not a big deal, we'll delete it next time
          }
        }
      }
    }.execute();
  }

  @NotNull
  public String getDescription() {
    return "Groovy to java source code generator";
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  public static Collection<VirtualFile> generateItems(final GroovyToJavaGenerator generator,
                                                      final VirtualFile item,
                                                      final VirtualFile outputRootDirectory,
                                                      CompileContext context,
                                                      final Project project) {
    ProgressIndicator indicator = context.getProgressIndicator();
    indicator.setText("Generating stubs for " + item.getName() + "...");

    if (LOG.isDebugEnabled()) {
      LOG.debug("Generating stubs for " + item.getName() + "...");
    }

    final Map<String, CharSequence> output = ApplicationManager.getApplication().runReadAction(new Computable<Map<String, CharSequence>>() {
      public Map<String, CharSequence> compute() {
        return generator.generateStubs((GroovyFile)PsiManager.getInstance(project).findFile(item));
      }
    });
    return writeStubs(outputRootDirectory, output, item);
  }

  private static List<VirtualFile> writeStubs(VirtualFile outputRootDirectory, Map<String, CharSequence> output, VirtualFile src) {
    final ArrayList<VirtualFile> stubs = CollectionFactory.arrayList();
    for (String relativePath : output.keySet()) {
      final File stubFile = new File(outputRootDirectory.getPath(), relativePath);
      FileUtil.createIfDoesntExist(stubFile);
      try {
        FileUtil.writeToFile(stubFile, output.get(relativePath).toString().getBytes(src.getCharset()));
      }
      catch (IOException e) {
        LOG.error(e);
      }
      CompilerUtil.refreshIOFile(stubFile);
      ContainerUtil.addIfNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(stubFile), stubs);
    }
    return stubs;
  }
}

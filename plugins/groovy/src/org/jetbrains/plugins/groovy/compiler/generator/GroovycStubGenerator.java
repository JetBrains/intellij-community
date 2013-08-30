/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Pair;
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
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootType;
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
    final ExcludedEntriesConfiguration excluded = GroovyCompilerConfiguration.getExcludeConfiguration(myProject);
    
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") FactoryMap<Pair<Module, Boolean>, Boolean> hasJava = new FactoryMap<Pair<Module, Boolean>, Boolean>() {
      @Override
      protected Boolean create(Pair<Module, Boolean> key) {
        return containsJavaSources(key.first, key.second);
      }
    };

    ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();

    List<VirtualFile> total = new ArrayList<VirtualFile>();
    for (final VirtualFile virtualFile : virtualFiles) {
      if (!excluded.isExcluded(virtualFile) &&
          GroovyNamesUtil.isIdentifier(virtualFile.getNameWithoutExtension())) {
        Module module = index.getModuleForFile(virtualFile);
        if (module == null || hasJava.get(Pair.create(module, index.isInTestSourceContent(virtualFile)))) {
          total.add(virtualFile);
        }
      }
    }

    if (total.isEmpty()) {
      return;
    }

    //long l = System.currentTimeMillis();
    super.compile(compileContext, moduleChunk, VfsUtil.toVirtualFileArray(total), sink);
    //System.out.println("Stub generation took " + (System.currentTimeMillis() - l));
  }

  private static boolean containsJavaSources(Module module, boolean inTests) {
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    List<VirtualFile> roots = inTests ? rootManager.getSourceRoots(JavaSourceRootType.TEST_SOURCE) : rootManager.getSourceRoots(JavaModuleSourceRootTypes.SOURCES);
    for (VirtualFile dir : roots) {
      if (!rootManager.getFileIndex().iterateContentUnderDirectory(dir, new ContentIterator() {
        @Override
        public boolean processFile(VirtualFile fileOrDir) {
          if (!fileOrDir.isDirectory() && JavaFileType.INSTANCE == fileOrDir.getFileType()) {
            return false;
          }
          return true;
        }
      })) {
        return true;
      }
    }
    return false;
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

    ProgressIndicator indicator = compileContext.getProgressIndicator();
    indicator.pushState();

    try {
      final GroovyToJavaGenerator generator = new GroovyToJavaGenerator(myProject, new HashSet<VirtualFile>(toCompile));
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

  private static File getStubOutput(Module module, boolean tests) {
    final Project project = module.getProject();
    final String rootPath = CompilerPaths.getGeneratedDataDirectory(project).getPath() + "/" + GROOVY_STUBS + "/";
    return new File(rootPath + module.getName() + "/" + (tests ? "tests" : "production") + "/");
  }

  @Nullable
  public static PsiClass findClassByStub(Project project, VirtualFile stubFile) {
    final String[] components = StringUtil.trimEnd(stubFile.getPath(), ".java").split("[\\\\/]");
    final int stubs = Arrays.asList(components).indexOf(GROOVY_STUBS);
    if (stubs < 0 || stubs >= components.length - 3) return null;

    final String moduleName = components[stubs + 1];
    final Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
    if (module == null) return null;

    final String fqn = StringUtil.join(Arrays.asList(components).subList(stubs + 3, components.length), ".");
    return JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.moduleScope(module));
  }

  private void cleanDirectory(final VirtualFile dir) {
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        AccessToken token = WriteAction.start();
        try {
          VfsUtil.processFilesRecursively(dir, new Processor<VirtualFile>() {
            @Override
            public boolean process(VirtualFile virtualFile) {
              if (!virtualFile.isDirectory()) {
                TranslatingCompilerFilesMonitor.removeSourceInfo(virtualFile);
                try {
                  virtualFile.delete(this);
                }
                catch (IOException e) {
                  LOG.info(e);
                }
              }
              return true;
            }
          });
        }
        finally {
          token.finish();
        }
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      assert ApplicationManager.getApplication().isUnitTestMode();
      runnable.run();
    } else {
      ApplicationManager.getApplication().invokeAndWait(runnable, ModalityState.NON_MODAL);
    }
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

    final Map<String, CharSequence> output;

    AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();

    try {
      output = generator.generateStubs((GroovyFile)PsiManager.getInstance(project).findFile(item));
    }
    finally {
      accessToken.finish();
    }

    return writeStubs(outputRootDirectory, output, item);
  }

  private static List<VirtualFile> writeStubs(VirtualFile outputRootDirectory, Map<String, CharSequence> output, VirtualFile src) {
    final ArrayList<VirtualFile> stubs = ContainerUtil.newArrayList();
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

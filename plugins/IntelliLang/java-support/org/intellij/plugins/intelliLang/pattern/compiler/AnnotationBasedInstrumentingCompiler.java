/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.intelliLang.pattern.compiler;

import com.intellij.compiler.PsiClassWriter;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.DataInput;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Based on NotNullVerifyingCompiler, kindly provided by JetBrains for reference.
 */
public abstract class AnnotationBasedInstrumentingCompiler implements ClassInstrumentingCompiler {
  private static final Logger LOG = Logger.getInstance("org.intellij.lang.pattern.compiler.AnnotationBasedInstrumentingCompiler");

  public AnnotationBasedInstrumentingCompiler() {
  }

  @NotNull
  public ProcessingItem[] getProcessingItems(final CompileContext context) {
    if (!isEnabled()) {
      return ProcessingItem.EMPTY_ARRAY;
    }

    final Project project = context.getProject();
    final Set<InstrumentationItem> result = new HashSet<InstrumentationItem>();
    final PsiSearchHelper searchHelper = PsiManager.getInstance(context.getProject()).getSearchHelper();

    DumbService.getInstance(project).waitForSmartMode();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final String[] names = getAnnotationNames(project);
        for (String name : names) {
          final GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

          final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.allScope(project));
          if (psiClass == null) {
            context.addMessage(CompilerMessageCategory.ERROR, "Cannot find class " + name, null, -1, -1);
            continue;
          }

          // wow, this is a sweet trick... ;)
          searchHelper.processAllFilesWithWord(StringUtil.getShortName(name), scope, new Processor<PsiFile>() {
            public boolean process(PsiFile psifile) {
              if (StdLanguages.JAVA == psifile.getLanguage() && psifile.getVirtualFile() != null && psifile instanceof PsiJavaFile) {
                addClassFiles((PsiJavaFile)psifile, result, project);
              }
              return true;
            }
          }, true);
        }
      }
    });
    return result.toArray(new ProcessingItem[result.size()]);
  }

  private static void addClassFiles(PsiJavaFile srcFile, Set<InstrumentationItem> result, final Project project) {

    final VirtualFile sourceFile = srcFile.getVirtualFile();
    assert sourceFile != null;

    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    final Module module = index.getModuleForFile(sourceFile);
    if (module != null) {
      final Sdk jdk = ModuleRootManager.getInstance(module).getSdk();
      final boolean jdk6;
      if (jdk != null) {
        final String versionString = jdk.getVersionString();
        jdk6 = versionString != null && (versionString.contains("1.6") || versionString.contains("6.0"));
      }
      else {
        jdk6 = false;
      }

      final CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
      final VirtualFile compilerOutputPath = extension != null ? extension.getCompilerOutputPath() : null;
      if (compilerOutputPath != null) {
        final String packageName = srcFile.getPackageName();
        final VirtualFile packageDir =
            packageName.length() > 0 ? compilerOutputPath.findFileByRelativePath(packageName.replace('.', '/')) : compilerOutputPath;

        if (packageDir != null && packageDir.isDirectory()) {
          final PsiClass[] classes = srcFile.getClasses();
          final VirtualFile[] children = packageDir.getChildren();
          for (VirtualFile classFile : children) {
            if (classFile.isDirectory() || !"class".equals(classFile.getExtension())) {
              // no point in looking at directories or non-class files
              continue;
            }
            final String name = classFile.getName();
            for (PsiClass clazz : classes) {
              final String className = clazz.getName();
              if (className != null && name.startsWith(className)) {
                result.add(new InstrumentationItem(classFile, jdk6));
              }
            }
          }
        }
      }
    }
  }

  public ProcessingItem[] process(final CompileContext context, final ProcessingItem[] items) {
    final ProgressIndicator progressIndicator = context.getProgressIndicator();
    progressIndicator.setText(getProgressMessage());

    final Project project = context.getProject();
    final ArrayList<ProcessingItem> result = new ArrayList<ProcessingItem>(items.length);
    ApplicationManager.getApplication().runReadAction(new Runnable() {

      public void run() {
        int filesProcessed = 0;

        for (ProcessingItem pi : items) {
          final InstrumentationItem item = ((InstrumentationItem)pi);
          final VirtualFile classFile = item.getClassFile();

          try {
            final byte[] bytes = classFile.contentsToByteArray();
            final ClassReader classreader;
            try {
              classreader = new ClassReader(bytes, 0, bytes.length);
            }
            catch (Exception e) {
              LOG.debug("ASM failed to read class file <" + classFile.getPresentableUrl() + ">", e);
              continue;
            }

            final ClassWriter classwriter = new PsiClassWriter(project, item.isJDK6());
            final Instrumenter instrumenter = createInstrumenter(classwriter);

            classreader.accept(instrumenter, 0);

            if (instrumenter.instrumented()) {
              // only dump the class if it has actually been instrumented
              final FileOutputStream out = new FileOutputStream(classFile.getPath());
              try {
                out.write(classwriter.toByteArray());
              }
              finally {
                out.close();
              }
            }

            result.add(item);
            progressIndicator.setFraction(++filesProcessed / (double)items.length);
          }
          catch (InstrumentationException e) {
            context.addMessage(CompilerMessageCategory.ERROR, "[" + getDescription() + "]: " + e.getLocalizedMessage(), null, -1, -1);
          }
          catch (IOException e) {
            context.addMessage(CompilerMessageCategory.ERROR, "[" + getDescription() + "]: " + e.getLocalizedMessage(), null, -1, -1);
          }
        }
      }
    });
    return result.toArray(new ProcessingItem[result.size()]);
  }

  protected abstract boolean isEnabled();

  protected abstract String[] getAnnotationNames(Project project);

  protected abstract Instrumenter createInstrumenter(ClassWriter classwriter);

  protected abstract String getProgressMessage();

  public boolean validateConfiguration(CompileScope compilescope) {
    return true;
  }

  @Nullable
  public ValidityState createValidityState(DataInput datainputstream) throws IOException {
//        return TimestampValidityState.load(datainputstream);
    return null;
  }
}

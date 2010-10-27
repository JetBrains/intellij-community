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
package org.jetbrains.android.compiler;

import com.intellij.CommonBundle;
import com.intellij.compiler.impl.CompileContextImpl;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.compiler.progress.CompilerTask;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.GeneratingCompiler;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class AndroidCompileUtil {
  private static final Pattern ourMessagePattern = Pattern.compile("(.+):(\\d+):.+");

  private AndroidCompileUtil() {
  }

  static void addMessages(final CompileContext context, final Map<CompilerMessageCategory, List<String>> messages) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (context.getProject().isDisposed()) return;
        for (CompilerMessageCategory category : messages.keySet()) {
          List<String> messageList = messages.get(category);
          for (String message : messageList) {
            String url = null;
            int line = -1;
            Matcher matcher = ourMessagePattern.matcher(message);
            if (matcher.matches()) {
              String fileName = matcher.group(1);
              if (new File(fileName).exists()) {
                url = "file://" + fileName;
                line = Integer.parseInt(matcher.group(2));
              }
            }
            context.addMessage(category, message, url, line, -1);
          }
        }
      }
    });
  }

  public static void createSourceRootIfNotExist(@NotNull final String path, @NotNull final Module module) {
    final ModuleRootManager manager = ModuleRootManager.getInstance(module);
    final File rootFile = new File(path);
    final boolean created;
    if (!rootFile.exists()) {
      if (!rootFile.mkdir()) return;
      created = true;
    }
    else {
      created = false;
    }
    final Project project = module.getProject();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (project.isDisposed()) return;
        final VirtualFile root;
        if (created) {
          root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(rootFile);
        }
        else {
          root = LocalFileSystem.getInstance().findFileByIoFile(rootFile);
        }
        if (root != null) {
          for (VirtualFile existingRoot : manager.getSourceRoots()) {
            if (existingRoot == root) return;
          }
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              addSourceRoot(manager, root);
            }
          });
        }
      }
    }, project.getDisposed());
  }

  public static void addSourceRoot(final ModuleRootManager manager, @NotNull final VirtualFile root) {
    final ModifiableRootModel model = manager.getModifiableModel();
    ContentEntry contentEntry = null;
    for (ContentEntry candidate : model.getContentEntries()) {
      VirtualFile contentRoot = candidate.getFile();
      if (contentRoot != null && VfsUtil.isAncestor(contentRoot, root, false)) {
        contentEntry = candidate;
      }
    }
    if (contentEntry == null) {
      contentEntry = model.addContentEntry(root);
    }
    contentEntry.addSourceFolder(root, false);
    model.commit();
  }

  public static void generate(final Module module, final GeneratingCompiler compiler, boolean withDependentModules) {
    if (withDependentModules) {
      Set<Module> modules = new HashSet<Module>();
      collectModules(module, modules, ModuleManager.getInstance(module.getProject()).getModules());
      for (Module module1 : modules) {
        generate(module1, compiler);
      }
    }
    else {
      generate(module, compiler);
    }
  }

  public static void generate(final Module module, GeneratingCompiler compiler) {
    assert !ApplicationManager.getApplication().isDispatchThread();
    final CompileContext[] contextWrapper = new CompileContext[1];
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        Project project = module.getProject();
        if (project.isDisposed()) return;
        CompilerTask task = new CompilerTask(project, true, "", true);
        CompileScope scope = new ModuleCompileScope(module, false);
        contextWrapper[0] = new CompileContextImpl(project, task, scope, null, false, false);
      }
    });
    generate(compiler, contextWrapper[0]);
  }

  public static void generate(GeneratingCompiler compiler, CompileContext context) {
    if (context != null) {
      GeneratingCompiler.GenerationItem[] items = compiler.getGenerationItems(context);
      compiler.generate(context, items, null);
    }
  }

  private static void collectModules(Module module, Set<Module> result, Module[] allModules) {
    if (!result.add(module)) {
      return;
    }
    for (Module otherModule : allModules) {
      if (ModuleRootManager.getInstance(otherModule).isDependsOn(module)) {
        collectModules(otherModule, result, allModules);
      }
    }
  }

  // must be invoked in a read action!

  public static void removeDuplicatingClasses(final Module module, @NotNull final String packageName, @NotNull String className,
                                              @Nullable final File classFile) {
    final Project project = module.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final String interfaceQualifiedName = packageName + '.' + className;
    PsiClass[] classes = facade.findClasses(interfaceQualifiedName, GlobalSearchScope.moduleScope(module));
    for (PsiClass c : classes) {
      PsiFile psiFile = c.getContainingFile();
      if (className.equals(FileUtil.getNameWithoutExtension(psiFile.getName()))) {
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile != null) {
          final String path = virtualFile.getPath();
          File f = new File(path);
          if (!f.equals(classFile) && f.exists()) {
            if (f.delete()) {
              virtualFile.refresh(true, false);
            }
            else {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  Messages.showErrorDialog(project, "Can't delete file " + path, CommonBundle.getErrorTitle());
                }
              }, project.getDisposed());
            }
          }
        }
      }
    }
  }

  @NotNull
  public static String[] collectResourceDirs(AndroidFacet facet) {
    List<String> result = new ArrayList<String>();
    Module module = facet.getModule();
    VirtualFile resourcesDir = AndroidAptCompiler.getResourceDirForApkCompiler(module, facet);
    if (resourcesDir != null) {
      result.add(resourcesDir.getPath());
    }
    for (AndroidFacet depFacet : AndroidUtils.getAllAndroidDependencies(module, true)) {
      VirtualFile depResourceDir = AndroidAptCompiler.getResourceDirForApkCompiler(depFacet.getModule(), facet);
      if (depResourceDir != null) {
        result.add(depResourceDir.getPath());
      }
    }
    return result.toArray(new String[result.size()]);
  }
}

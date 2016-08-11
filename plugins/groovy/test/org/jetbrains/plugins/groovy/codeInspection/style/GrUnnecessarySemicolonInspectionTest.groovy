/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.style

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

@CompileStatic
class GrUnnecessarySemicolonInspectionTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  void 'test simple'() {
    doTest '''\
 ;<caret> print 1;
; ; print 2 ; ;
 ; ; ;print 3 ;;;

print 1; print 2
print 1; ;print 2
print 1 ;;  print 2
print 1 ; ;;print 2
 ;
''', '''\
  print 1
  print 2  
   print 3 

print 1; print 2
print 1; print 2
print 1 ;  print 2
print 1 ; print 2
 
'''
  }

  void 'test closure'() {
    doTest '''\
print(a);
{->print 3}
print(a); <caret>;;
{->}
print(a);; ;{->}
''', '''\
print(a);
{->print 3}
print(a); 
{->}
print(a); {->}
'''
  }

  void 'test traditional for'() {
    doTest 'for(int i = 0; i < 5; i++);<caret>',
           'for(int i = 0; i < 5; i++)'
  }

  void 'test class members'() {
    doTest '''\
class A {
  def x;<caret>
  def y;
  def a; def b
}
''', '''\
class A {
  def x
  def y
  def a; def b
}
'''
  }

  private doTest(String before, String after) {
    fixture.with {
      enableInspections GrUnnecessarySemicolonInspection
      configureByText '_.groovy', before
//      checkHighlighting()
      launchAction findSingleIntention("Fix all 'Unnecessary semicolon'")
      checkResult after
    }
  }

  void testBig() {
    myFixture.enableInspections(GrUnresolvedAccessInspection)
    doTest '''\
package com.intellij.refactoring.util;<caret>

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author ven
 */
public class CommonRefactoringUtil {
  private CommonRefactoringUtil() {}

  public static void showErrorMessage(String title, String message, @Nullable @NonNls String helpId, @NotNull Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) throw new RuntimeException(message);
    RefactoringMessageDialog dialog = new RefactoringMessageDialog(title, message, helpId, "OptionPane.errorIcon", false, project);
    dialog.show();
  }

  /**
   * Fatal refactoring problem during unit test run. Corresponds to message of modal dialog shown during user driven refactoring.
   */
  public static class RefactoringErrorHintException extends RuntimeException {
    public RefactoringErrorHintException(String message) {
      super(message);
    }
  }

  public static void showErrorHint(Project project, @Nullable Editor editor, String message, String title, @Nullable @NonNls String helpId) {
    if (ApplicationManager.getApplication().isUnitTestMode()) throw new RefactoringErrorHintException(message);

    if (editor == null) {
      showErrorMessage(title, message, helpId, project);
    }
    else {
      HintManager.getInstance().showErrorHint(editor, message);
    }
  }

  @NonNls
  public static String htmlEmphasize(String text) {
    return "<b><code>" + text + "</code></b>";
  }

  public static boolean checkReadOnlyStatus(@NotNull PsiElement element) {
    final VirtualFile file = element.getContainingFile().getVirtualFile();
    return file != null && !ReadonlyStatusHandler.getInstance(element.getProject()).ensureFilesWritable(file).hasReadonlyFiles();
  }

  public static boolean checkReadOnlyStatus(@NotNull Project project, @NotNull PsiElement element) {
    return checkReadOnlyStatus(element, project, RefactoringBundle.message("refactoring.cannot.be.performed"));
  }

  public static boolean checkReadOnlyStatus(@NotNull Project project, @NotNull PsiElement... elements) {
    return checkReadOnlyStatus(Arrays.asList(elements), project, RefactoringBundle.message("refactoring.cannot.be.performed"), false, true);
  }

  public static boolean checkReadOnlyStatus(@NotNull PsiElement element, @NotNull Project project, String messagePrefix) {
    return element.isWritable() ||
           checkReadOnlyStatus(Collections.singleton(element), project, messagePrefix, false, true);
  }

  public static boolean checkReadOnlyStatusRecursively(@NotNull Project project, @NotNull Collection<? extends PsiElement> elements) {
    return checkReadOnlyStatus(elements, project, RefactoringBundle.message("refactoring.cannot.be.performed"), true, false);
  }
  public static boolean checkReadOnlyStatusRecursively(@NotNull Project project, @NotNull Collection<? extends PsiElement> elements, boolean notifyOnFail) {
    return checkReadOnlyStatus(elements, project, RefactoringBundle.message("refactoring.cannot.be.performed"), true, notifyOnFail);
  }

  private static boolean checkReadOnlyStatus(@NotNull Collection<? extends PsiElement> elements,
                                             @NotNull Project project,
                                             final String messagePrefix,
                                             boolean recursively,
                                             final boolean notifyOnFail) {
    final Collection<VirtualFile> readonly = new THashSet<VirtualFile>(); // not writable, but could be checked out
    final Collection<VirtualFile> failed = new THashSet<VirtualFile>();   // those located in jars
    boolean seenNonWritablePsiFilesWithoutVirtualFile = false;

    for (PsiElement element : elements) {
      if (element instanceof PsiDirectory) {
        final PsiDirectory dir = (PsiDirectory)element;
        final VirtualFile vFile = dir.getVirtualFile();
        if (vFile.getFileSystem() instanceof JarFileSystem) {
          failed.add(vFile);
        }
        else {
          if (recursively) {
            addVirtualFiles(vFile, readonly);
          }
          else {
            readonly.add(vFile);
          }
        }
      }
      else if (element instanceof PsiDirectoryContainer) {
        final PsiDirectory[] directories = ((PsiDirectoryContainer)element).getDirectories();
        for (PsiDirectory directory : directories) {
          VirtualFile virtualFile = directory.getVirtualFile();
          if (recursively) {
            if (virtualFile.getFileSystem() instanceof JarFileSystem) {
              failed.add(virtualFile);
            }
            else {
              addVirtualFiles(virtualFile, readonly);
            }
          }
          else {
            if (virtualFile.getFileSystem() instanceof JarFileSystem) {
              failed.add(virtualFile);
            }
            else {
              readonly.add(virtualFile);
            }
          }
        }
      }
      else if (element instanceof PsiCompiledElement) {
        final PsiFile file = element.getContainingFile();
        if (file != null) {
          failed.add(file.getVirtualFile());
        }
      }
      else {
        PsiFile file = element.getContainingFile();
        if (file == null) {
          if (!element.isWritable()) {
            seenNonWritablePsiFilesWithoutVirtualFile = true;
          }
        }
        else {
          final VirtualFile vFile = file.getVirtualFile();
          if (vFile != null) {
            readonly.add(vFile);
          }
          else {
            if (!element.isWritable()) {
              seenNonWritablePsiFilesWithoutVirtualFile = true;
            }
          }
        }
      }
    }

    final VirtualFile[] files = VfsUtil.toVirtualFileArray(readonly);
    final ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files);
    ContainerUtil.addAll(failed, status.getReadonlyFiles());
    if (notifyOnFail && (!failed.isEmpty() || seenNonWritablePsiFilesWithoutVirtualFile && readonly.isEmpty())) {
      StringBuilder message = new StringBuilder(messagePrefix);
      message.append('\\n');
      int i = 0;
      for (VirtualFile virtualFile : failed) {
        final String presentableUrl = virtualFile.getPresentableUrl();
        final String subj = virtualFile.isDirectory()
                            ? RefactoringBundle.message("directory.description", presentableUrl)
                            : RefactoringBundle.message("file.description", presentableUrl);
        if (virtualFile.getFileSystem() instanceof JarFileSystem) {
          message.append(RefactoringBundle.message("0.is.located.in.a.jar.file", subj));
        }
        else {
          message.append(RefactoringBundle.message("0.is.read.only", subj));
        }
        if (i++ > 20) {
          message.append("...\\n");
          break;
        }
      }
      showErrorMessage(RefactoringBundle.message("error.title"), message.toString(), null, project);
      return false;
    }

    return failed.isEmpty();
  }

  private static void addVirtualFiles(final VirtualFile vFile, final Collection<VirtualFile> list) {
    if (!vFile.isWritable()) {
      list.add(vFile);
    }
    if (!vFile.isSymLink()) {
      final VirtualFile[] children = vFile.getChildren();
      if (children != null) {
        final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
        for (VirtualFile virtualFile : children) {
          if (fileTypeManager.isFileIgnored(virtualFile)) continue;
          addVirtualFiles(virtualFile, list);
        }
      }
    }
  }

  public static String capitalize(String text) {
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  public static boolean isAncestor(final PsiElement resolved, final Collection<? extends PsiElement> scopes) {
    for (final PsiElement scope : scopes) {
      if (PsiTreeUtil.isAncestor(scope, resolved, false)) return true;
    }
    return false;
  }
}
''', '''\
package com.intellij.refactoring.util

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringBundle
import com.intellij.util.containers.ContainerUtil
import gnu.trove.THashSet
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import java.util.Arrays
import java.util.Collection
import java.util.Collections

/**
 * @author ven
 */
public class CommonRefactoringUtil {
  private CommonRefactoringUtil() {}

  public static void showErrorMessage(String title, String message, @Nullable @NonNls String helpId, @NotNull Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) throw new RuntimeException(message)
    RefactoringMessageDialog dialog = new RefactoringMessageDialog(title, message, helpId, "OptionPane.errorIcon", false, project)
    dialog.show()
  }

  /**
   * Fatal refactoring problem during unit test run. Corresponds to message of modal dialog shown during user driven refactoring.
   */
  public static class RefactoringErrorHintException extends RuntimeException {
    public RefactoringErrorHintException(String message) {
      super(message)
    }
  }

  public static void showErrorHint(Project project, @Nullable Editor editor, String message, String title, @Nullable @NonNls String helpId) {
    if (ApplicationManager.getApplication().isUnitTestMode()) throw new RefactoringErrorHintException(message)

    if (editor == null) {
      showErrorMessage(title, message, helpId, project)
    }
    else {
      HintManager.getInstance().showErrorHint(editor, message)
    }
  }

  @NonNls
  public static String htmlEmphasize(String text) {
    return "<b><code>" + text + "</code></b>"
  }

  public static boolean checkReadOnlyStatus(@NotNull PsiElement element) {
    final VirtualFile file = element.getContainingFile().getVirtualFile()
    return file != null && !ReadonlyStatusHandler.getInstance(element.getProject()).ensureFilesWritable(file).hasReadonlyFiles()
  }

  public static boolean checkReadOnlyStatus(@NotNull Project project, @NotNull PsiElement element) {
    return checkReadOnlyStatus(element, project, RefactoringBundle.message("refactoring.cannot.be.performed"))
  }

  public static boolean checkReadOnlyStatus(@NotNull Project project, @NotNull PsiElement... elements) {
    return checkReadOnlyStatus(Arrays.asList(elements), project, RefactoringBundle.message("refactoring.cannot.be.performed"), false, true)
  }

  public static boolean checkReadOnlyStatus(@NotNull PsiElement element, @NotNull Project project, String messagePrefix) {
    return element.isWritable() ||
           checkReadOnlyStatus(Collections.singleton(element), project, messagePrefix, false, true)
  }

  public static boolean checkReadOnlyStatusRecursively(@NotNull Project project, @NotNull Collection<? extends PsiElement> elements) {
    return checkReadOnlyStatus(elements, project, RefactoringBundle.message("refactoring.cannot.be.performed"), true, false)
  }
  public static boolean checkReadOnlyStatusRecursively(@NotNull Project project, @NotNull Collection<? extends PsiElement> elements, boolean notifyOnFail) {
    return checkReadOnlyStatus(elements, project, RefactoringBundle.message("refactoring.cannot.be.performed"), true, notifyOnFail)
  }

  private static boolean checkReadOnlyStatus(@NotNull Collection<? extends PsiElement> elements,
                                             @NotNull Project project,
                                             final String messagePrefix,
                                             boolean recursively,
                                             final boolean notifyOnFail) {
    final Collection<VirtualFile> readonly = new THashSet<VirtualFile>() // not writable, but could be checked out
    final Collection<VirtualFile> failed = new THashSet<VirtualFile>()   // those located in jars
    boolean seenNonWritablePsiFilesWithoutVirtualFile = false

    for (PsiElement element : elements) {
      if (element instanceof PsiDirectory) {
        final PsiDirectory dir = (PsiDirectory)element
        final VirtualFile vFile = dir.getVirtualFile()
        if (vFile.getFileSystem() instanceof JarFileSystem) {
          failed.add(vFile)
        }
        else {
          if (recursively) {
            addVirtualFiles(vFile, readonly)
          }
          else {
            readonly.add(vFile)
          }
        }
      }
      else if (element instanceof PsiDirectoryContainer) {
        final PsiDirectory[] directories = ((PsiDirectoryContainer)element).getDirectories()
        for (PsiDirectory directory : directories) {
          VirtualFile virtualFile = directory.getVirtualFile()
          if (recursively) {
            if (virtualFile.getFileSystem() instanceof JarFileSystem) {
              failed.add(virtualFile)
            }
            else {
              addVirtualFiles(virtualFile, readonly)
            }
          }
          else {
            if (virtualFile.getFileSystem() instanceof JarFileSystem) {
              failed.add(virtualFile)
            }
            else {
              readonly.add(virtualFile)
            }
          }
        }
      }
      else if (element instanceof PsiCompiledElement) {
        final PsiFile file = element.getContainingFile()
        if (file != null) {
          failed.add(file.getVirtualFile())
        }
      }
      else {
        PsiFile file = element.getContainingFile()
        if (file == null) {
          if (!element.isWritable()) {
            seenNonWritablePsiFilesWithoutVirtualFile = true
          }
        }
        else {
          final VirtualFile vFile = file.getVirtualFile()
          if (vFile != null) {
            readonly.add(vFile)
          }
          else {
            if (!element.isWritable()) {
              seenNonWritablePsiFilesWithoutVirtualFile = true
            }
          }
        }
      }
    }

    final VirtualFile[] files = VfsUtil.toVirtualFileArray(readonly)
    final ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files)
    ContainerUtil.addAll(failed, status.getReadonlyFiles())
    if (notifyOnFail && (!failed.isEmpty() || seenNonWritablePsiFilesWithoutVirtualFile && readonly.isEmpty())) {
      StringBuilder message = new StringBuilder(messagePrefix)
      message.append('\\n')
      int i = 0
      for (VirtualFile virtualFile : failed) {
        final String presentableUrl = virtualFile.getPresentableUrl()
        final String subj = virtualFile.isDirectory()
                            ? RefactoringBundle.message("directory.description", presentableUrl)
                            : RefactoringBundle.message("file.description", presentableUrl)
        if (virtualFile.getFileSystem() instanceof JarFileSystem) {
          message.append(RefactoringBundle.message("0.is.located.in.a.jar.file", subj))
        }
        else {
          message.append(RefactoringBundle.message("0.is.read.only", subj))
        }
        if (i++ > 20) {
          message.append("...\\n")
          break
        }
      }
      showErrorMessage(RefactoringBundle.message("error.title"), message.toString(), null, project)
      return false
    }

    return failed.isEmpty()
  }

  private static void addVirtualFiles(final VirtualFile vFile, final Collection<VirtualFile> list) {
    if (!vFile.isWritable()) {
      list.add(vFile)
    }
    if (!vFile.isSymLink()) {
      final VirtualFile[] children = vFile.getChildren()
      if (children != null) {
        final FileTypeManager fileTypeManager = FileTypeManager.getInstance()
        for (VirtualFile virtualFile : children) {
          if (fileTypeManager.isFileIgnored(virtualFile)) continue
          addVirtualFiles(virtualFile, list)
        }
      }
    }
  }

  public static String capitalize(String text) {
    return Character.toUpperCase(text.charAt(0)) + text.substring(1)
  }

  public static boolean isAncestor(final PsiElement resolved, final Collection<? extends PsiElement> scopes) {
    for (final PsiElement scope : scopes) {
      if (PsiTreeUtil.isAncestor(scope, resolved, false)) return true
    }
    return false
  }
}
'''
  }
}

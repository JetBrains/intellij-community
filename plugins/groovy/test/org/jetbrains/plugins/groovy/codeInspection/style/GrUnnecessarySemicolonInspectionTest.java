// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style;

import com.intellij.testFramework.LightProjectDescriptor;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;

public class GrUnnecessarySemicolonInspectionTest extends LightGroovyTestCase {
  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  public void testSimple() {
    doTest("""
              ;<caret> print 1;
             ; ; print 2 ; ;
              ; ; ;print 3 ;;;
             
             print 1; print 2
             print 1; ;print 2
             print 1 ;;  print 2
             print 1 ; ;;print 2
              ;
             """,
           """
               print 1
               print 2 \s
                print 3\s
             
             print 1; print 2
             print 1; print 2
             print 1 ;  print 2
             print 1 ; print 2
             \s
             """);
  }

  public void testClosure() {
    doTest("""
             print(a);
             {->print 3}
             print(a); <caret>;;
             {->}
             print(a);; ;{->}
             print(a);; /*asd*/;
             {->}
             """,
           """
             print(a);
             {->print 3}
             print(a);\s
             {->}
             print(a); {->}
             print(a); /*asd*/
             {->}
             """);
  }

  public void testTraditionalFor() {
    doTest("for(int i = 0; i < 5; i++);<caret>", "for(int i = 0; i < 5; i++)");
  }

  public void testTraditionalForWithoutUpdate() {
    doTest("for (int i = 0; i < 10;) {}");
  }

  public void testTraditionalForWithoutCondition() {
    doTest("for (int i = 0; ; i++) {}");
  }

  public void testWithinMethod() {
    doTest("def foo() {; ;1;;/*asd*/;54; ;<caret>;; }", "def foo() { 1;/*asd*/54  }");
  }

  public void testWithinClosure() {
    doTest("foo {; ;1;;/*asd*/;54; ;<caret>;; }", "foo { 1;/*asd*/54  }");
  }

  public void testWithinClosureWithArrow() {
    doTest("foo { -> ; ;1;;/*asd*/;54; ;<caret>;; }", "foo { ->  1;/*asd*/54  }");
  }

  public void testClassMembers() {
    doTest("""
             class A {
               def x;<caret>
               def y;
               def a; def b
             }
             """,
           """
             class A {
               def x
               def y
               def a; def b
             }
             """);
  }

  public void testAfterPackageDefinition() {
    doTest("package com.foo<caret>; import java.lang.String");
    doTest("""
             package com.foo<caret>;\s
             import java.lang.String
             """,
           """
             package com.foo\s
             import java.lang.String
             """);
  }

  public void testAfterImportDefinition() {
    doTest("import java.lang.String<caret>; import java.lang.String");
    doTest("""
             import java.lang.String<caret>;
             import java.lang.String;
             """,
           """
             import java.lang.String
             import java.lang.String
             """);
  }

  public void testAfterEnumConstants() {
    doTest("""
             enum E {
               foo, bar, <caret>; \s
               E() {}
             }
             """);
    doTest("""
             enum E {
               foo, bar,\s
              \s
               <caret>; \s
              \s
              \s
               E() {}
             }
             """);
  }

  public void testBig() {
    myFixture.enableInspections(GrUnresolvedAccessInspection.class);
    doTest("""
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
             """,
           """
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
             """);
  }

  private void doTest(final String before, final String after) {
    myFixture.enableInspections(GrUnnecessarySemicolonInspection.class);
    myFixture.configureByText("_.groovy", before);
    myFixture.launchAction(myFixture.findSingleIntention("Fix all 'Unnecessary semicolon'"));
    myFixture.checkResult(after);
  }

  private void doTest(final String text) {
    myFixture.enableInspections(GrUnnecessarySemicolonInspection.class);
    myFixture.configureByText("_.groovy", text);
    TestCase.assertNull(myFixture.getAvailableIntention("Fix all 'Unnecessary semicolon'"));
  }
}

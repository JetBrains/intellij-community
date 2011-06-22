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

package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import junit.framework.AssertionFailedError;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * @author Maxim.Medvedev
 */
public class GroovyMoveClassTest extends CodeInsightTestCase {
  protected static String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/move/moveClass/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final FileTemplateManager templateManager = FileTemplateManager.getInstance();
    FileTemplate temp = templateManager.getTemplate("GroovyClass.groovyForTest");
    if (temp != null) templateManager.removeTemplate(temp);

    temp = templateManager.addTemplate("GroovyClass.groovyForTest", "groovy");
    temp.setText("#if ( $PACKAGE_NAME != \"\" )package ${PACKAGE_NAME}\n" + "#end\n" + "class ${NAME} {\n" + "}");

    temp = templateManager.getTemplate("GroovyClass.groovy");
    if (temp != null) templateManager.removeTemplate(temp);

    temp = templateManager.addTemplate("GroovyClass.groovy", "groovy");
    temp.setText("#if ( $PACKAGE_NAME != \"\" )package ${PACKAGE_NAME}\n" + "#end\n" + "class ${NAME} {\n" + "}");
  }

  @Override
  protected void tearDown() throws Exception {
    final FileTemplateManager templateManager = FileTemplateManager.getInstance();
    FileTemplate temp = templateManager.getTemplate("GroovyClass.groovy");
    templateManager.removeTemplate(temp);

    temp = templateManager.getTemplate("GroovyClass.groovyForTest");
    templateManager.removeTemplate(temp);
    super.tearDown();
  }

  public void testMoveMultiple() throws Exception {
    doTest("moveMultiple1", new String[]{"pack1.Class1", "pack1.Class2"}, "pack2");
  }

  public void testSecondaryClass() throws Exception {
    doTest("secondaryClass", new String[]{"pack1.Class2"}, "pack1");
  }

  public void testStringsAndComments() throws Exception {
    doTest("stringsAndComments", new String[]{"pack1.Class1"}, "pack2");
  }

  public void testStringsAndComments2() throws Exception {
    doTest("stringsAndComments2", new String[]{"pack1.AClass"}, "pack2");
  }

/*  public void testNonJava() throws Exception {
    doTest("nonJava", new String[]{"pack1.Class1"}, "pack2");
  }*/

  /* IMPLEMENT: getReferences() in JspAttributeValueImpl should be dealed with (soft refs?)

  public void testJsp() throws Exception{
    doTest("jsp", new String[]{"pack1.TestClass"}, "pack2");
  }
  */

  public void testLocalClass() throws Exception {
    doTest("localClass", new String[]{"pack1.A"}, "pack2");
  }

  public void testClassAndSecondary() throws Exception {
    doTest("classAndSecondary", new String[]{"pack1.Class1", "pack1.Class2"}, "pack2");
  }

  public void testIdeadev27996() throws Exception {
    doTest("ideadev27996", new String[]{"pack1.X"}, "pack2");
  }

  public void testScript() throws Exception {
    doTest("script", new String[]{"pack1.Xx"}, "pack2");
  }

  public void testTwoClasses() {
    doTest("twoClasses", new String[] {"p1.C1", "p1.C2"}, "p2");
  }

  public void testStaticImport() {
    doTest("staticImport", new String[] {"p1.C1"}, "p2");
  }

  public void testAliasImported() {
    doTest("aliasImported", new String[]{"p1.C1"}, "p2");
  }

  public void _testTwoModules() {
    doTwoModulesTest("twoModules", new String[]{"p1.C1"}, "p2");
  }

  private void performAction(String[] classNames, String newPackageName, int dirCount) {
    final PsiClass[] classes = new PsiClass[classNames.length];
    for (int i = 0; i < classes.length; i++) {
      String className = classNames[i];
      classes[i] = JavaPsiFacade.getInstance(getProject()).findClass(className, GlobalSearchScope.allScope(getProject()));
      assertNotNull("Class " + className + " not found", classes[i]);
    }

    PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage(newPackageName);
    assertNotNull("Package " + newPackageName + " not found", aPackage);
    final PsiDirectory[] dirs = aPackage.getDirectories();
    assertEquals(dirs.length, dirCount);

    final Application application = ApplicationManager.getApplication();
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      @Override
      public void run() {
        application.runWriteAction(new Runnable() {
          @Override
          public void run() {
            final PsiDirectory dir = dirs[dirs.length - 1];
            final SingleSourceRootMoveDestination moveDestination =
              new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(dir)), dir);
            new MoveClassesOrPackagesProcessor(getProject(), classes, moveDestination, true, true, null).run();
          }
        });
      }
    }, "", null);

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  private void doTest(String testName, String[] classNames, String newPackageName) {
    try {
      String root = PathManager.getHomePath().replace(File.separatorChar, '/') + getBasePath() + testName;

      String rootBefore = root + "/before";
      PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk17());
      VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(getProject(), myModule, rootBefore, myFilesToDelete);

      performAction(classNames, newPackageName, 1);

      String rootAfter = root + "/after";
      VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
      myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
      PlatformTestUtil.assertDirectoriesEqual(rootDir2, rootDir, PlatformTestUtil.CVS_FILE_FILTER);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void doTwoModulesTest(String testName, String[] classNames, String newPackageName) {
    try {
      String root = PathManager.getHomePath().replace(File.separatorChar, '/') + getBasePath() + testName;

      String rootBefore = root + "/before";
      PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk17());
      VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(getProject(), myModule, rootBefore, myFilesToDelete);
      final Module second = createModule("second");
      rootDir.findChild("second").createChildDirectory(this, "p2");
      performAction(classNames, newPackageName, 1);

      String rootAfter = root + "/after";
      VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
      myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
      PlatformTestUtil.assertDirectoriesEqual(rootDir2, rootDir, PlatformTestUtil.CVS_FILE_FILTER);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void assertDirsEquals(File d1, VirtualFile d2) {
    final File[] ch1 = d1.listFiles();
    final VirtualFile[] ch2 = d2.getChildren();
    Arrays.sort(ch1, new Comparator<File>() {
      @Override
      public int compare(File o1, File o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });

    Arrays.sort(ch2, new Comparator<VirtualFile>() {
      @Override
      public int compare(VirtualFile o1, VirtualFile o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });

    assertEquals("check dir '"+d1.getName()+"': ", ch1.length, ch2.length);
    for (int i = 1; i < ch1.length; i++) {
      assertEquals(ch1[i].isDirectory(), ch2[i].isDirectory());
      if (ch1[i].isDirectory() && !".svn".equals(ch1[i].getName())) {
        assertDirsEquals(ch1[i], ch2[i]);
      }
      else {
        assertEquals(ch1[i].getName(), ch2[i].getName());
        try {
          assertFilesEqual(ch1[i], ch2[i]);
        }
        catch (IOException e) {
          assertTrue(false);
        }
      }
    }
  }

  public static void assertFilesEqual(File f1, VirtualFile f2) throws IOException {

    final byte[] bytes1 = contentsToByteArray(f1);
    final byte[] bytes2 = f2.contentsToByteArray();

    String s1 = StringUtil.convertLineSeparators(new String(bytes1));
    String s2 = StringUtil.convertLineSeparators(new String(bytes2));

    try {
      assertEquals(s1, s2);
    }
    catch (AssertionFailedError e) {
      System.out.println(f1.getPath() + "\n" + f2.getPath());
      throw e;
    }
  }

  private static byte[] contentsToByteArray(File f) throws IOException {
    int b;
    final FileReader fileReader = new FileReader(f);
    try {
      ArrayList<Byte> bytes = new ArrayList<Byte>();
      while ((b = fileReader.read()) >= 0) {
        bytes.add((byte)b);
      }
      final byte[] res = new byte[bytes.size()];
      for (int i = 0; i < res.length; i++) {
        res[i] = bytes.get(i);
      }
      return res;
    }
    finally {
      fileReader.close();
    }
  }
}

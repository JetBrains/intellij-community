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

package org.jetbrains.plugins.groovy.refactoring.move
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination
import org.jetbrains.plugins.groovy.actions.GroovyTemplates
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author Maxim.Medvedev
 */
public class GroovyMoveClassTest extends GroovyMoveTestBase {
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "refactoring/move/moveClass/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final FileTemplateManager templateManager = FileTemplateManager.getInstance(getProject())
    FileTemplate temp = templateManager.getTemplate("GroovyClass.groovyForTest");
    if (temp != null) templateManager.removeTemplate(temp);

    temp = templateManager.addTemplate("GroovyClass.groovyForTest", "groovy");
    temp.text = '''\
#if ( $PACKAGE_NAME != \"\" )package ${PACKAGE_NAME}
#end
class ${NAME} {
}''';

    temp = templateManager.getTemplate(GroovyTemplates.GROOVY_CLASS);
    if (temp != null) templateManager.removeTemplate(temp);

    temp = templateManager.addTemplate(GroovyTemplates.GROOVY_CLASS, "groovy");
    temp.text = '''\
#if ( $PACKAGE_NAME != \"\" )package ${PACKAGE_NAME}
#end
class ${NAME} {
}''';
  }

  @Override
  protected void tearDown() throws Exception {
    final FileTemplateManager templateManager = FileTemplateManager.getInstance(getProject());
    FileTemplate temp = templateManager.getTemplate(GroovyTemplates.GROOVY_CLASS);
    templateManager.removeTemplate(temp);

    temp = templateManager.getTemplate("GroovyClass.groovyForTest");
    templateManager.removeTemplate(temp);
    super.tearDown();
  }

  public void testMoveMultiple1() throws Exception {
    doTest("pack2", "pack1.Class1", "pack1.Class2");
  }

  public void testSecondaryClass() throws Exception {
    doTest("pack1", "pack1.Class2");
  }

  public void testStringsAndComments() throws Exception {
    doTest("pack2", "pack1.Class1");
  }

  public void testStringsAndComments2() throws Exception {
    doTest("pack2", "pack1.AClass");
  }

  public void testLocalClass() throws Exception {
    doTest("pack2", "pack1.A");
  }

  public void testClassAndSecondary() throws Exception {
    doTest("pack2", "pack1.Class1", "pack1.Class2");
  }

  public void testIdeadev27996() throws Exception {
    doTest("pack2", "pack1.X");
  }

  public void testScript() throws Exception {
    doTest("pack2", "pack1.Xx");
  }

  public void testTwoClasses() {
    doTest("p2", "p1.C1", "p1.C2");
  }

  public void testStaticImport() {
    doTest("p2", "p1.C1");
  }

  public void testAliasImported() {
    doTest("p2", "p1.C1");
  }

  public boolean perform(VirtualFile root, String newPackageName, String... classNames) {
    final PsiClass[] classes = new PsiClass[classNames.length];
    for (int i = 0; i < classes.length; i++) {
      String className = classNames[i];
      classes[i] = JavaPsiFacade.getInstance(getProject()).findClass(className, GlobalSearchScope.allScope(getProject()));
      assertNotNull("Class " + className + " not found", classes[i]);
    }

    PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage(newPackageName);
    assertNotNull("Package " + newPackageName + " not found", aPackage);
    final PsiDirectory[] dirs = aPackage.getDirectories();

    final PsiDirectory dir = dirs[dirs.length - 1];
    final SingleSourceRootMoveDestination moveDestination =
      new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(dir)), dir);
    new MoveClassesOrPackagesProcessor(getProject(), classes, moveDestination, true, true, null).run();

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();

    return true
  }
}

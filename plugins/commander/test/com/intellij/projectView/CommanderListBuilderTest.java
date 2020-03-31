// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.commander.Commander;
import com.intellij.ide.commander.CommanderPanel;
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure;
import com.intellij.ide.projectView.impl.ClassesTreeStructureProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.uiDesigner.projectView.FormMergerTreeStructureProvider;
import com.intellij.util.IncorrectOperationException;
import junit.framework.TestCase;

import javax.swing.*;

public class CommanderListBuilderTest extends BaseProjectViewTestCase {
  private Commander myCommander;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myCommander = new Commander(myProject) {
      @Override
      protected void updateToolWindowTitle(final CommanderPanel activePanel) {
      }

      @Override
      protected AbstractProjectTreeStructure createProjectTreeStructure() {
        return getProjectTreeStructure();
      }
    };
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myCommander);
      myCommander = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testStandardProviders() {
    useStandardProviders();

    myCommander.enterElementInActivePanel(getContentDirectory());
    checkListInActivePanel("[ .. ]\n" + "PsiDirectory: src\n");

    myCommander.switchActivePanel();
    myCommander.enterElementInActivePanel(getPackageDirectory());
    checkListInActivePanel(
      "[ .. ]\n" + "Class1\n" + "Class2.java\n" + "Class4.java\n" + "Form1\n" + "Form1.form\n" + "Form2.form\n");

    CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        findClassInDirectory("Class1").setName("Class1_renamed");
      }
      catch (IncorrectOperationException e) {
        TestCase.fail();
      }
    }), null, null);


    checkListInActivePanel(
      "[ .. ]\n" + "Class1_renamed\n" + "Class2.java\n" + "Class4.java\n" + "Form1\n" + "Form1.form\n" + "Form2.form\n");
  }

  private PsiClass findClassInDirectory(final String className) {
    final PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(getPackageDirectory());
    for (PsiClass aClass : classes) {
      if (aClass.getName().equals(className)) {
        return aClass;
      }
    }
    TestCase.fail(className + " not found");
    return null;
  }

  public void testShowClassMembers() {
    useStandardProviders();
    myStructure.setShowMembers(true);
    PsiField field = findClassInDirectory("Class1").getFields()[1];
    myCommander.selectElementInRightPanel(field, field.getContainingFile().getVirtualFile());

    checkListInRightPanel("[ .. ]\n" + "InnerClass\n" + "getValue():int\n" + "myField1:boolean\n" + "myField2:boolean\n");
    checkSelectedElement(field, myCommander.getRightPanel());

    myCommander.selectElementInLeftPanel(getPackageDirectory(), getPackageDirectory().getVirtualFile());
    checkListInLeftPanel("[ .. ]\n" + "PsiDirectory: package1\n");
    checkSelectedElement(getPackageDirectory(), myCommander.getLeftPanel());

    myCommander.syncViews();
    myCommander.swapPanels();
  }

  public void testUpdateProjectView() {
    getProjectTreeStructure().setProviders(new ClassesTreeStructureProvider(myProject), new FormMergerTreeStructureProvider(myProject));

    myStructure.setShowMembers(true);
    final PsiClass formClass = findClassInDirectory("Form1");
    myCommander.selectElementInRightPanel(formClass, formClass.getContainingFile().getVirtualFile());

    checkListInRightPanel("[ .. ]\n" + "Form1\n" + "Form1.form\n");

    WriteCommandAction.runWriteCommandAction(null, () -> formClass.delete());


    PlatformTestUtil.waitForAlarm(600);

    checkListInRightPanel("[ .. ]\n" + "Class1\n" + "Class2.java\n" + "Class4.java\n" + "Form1.form\n" + "Form2.form\n");
  }

  private void checkListInLeftPanel(String expected) {
    checkListInPanel(myCommander.getLeftPanel(), expected);
  }

  private void checkListInRightPanel(String expected) {
    checkListInPanel(myCommander.getRightPanel(), expected);
  }

  private static void checkSelectedElement(Object field, CommanderPanel panel) {
    TestCase.assertEquals(field, panel.getSelectedElement());
  }

  private void checkListInActivePanel(String expected) {
    checkListInPanel(myCommander.getActivePanel(), expected);
  }

  private static void checkListInPanel(CommanderPanel activePanel, String expected) {
    BaseProjectViewTestCase.assertListsEqual((ListModel)activePanel.getModel(), expected);
  }
}

/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.tests;

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CheckboxTreeBase;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.GuiUtils;
import git4idea.config.GitVcsSettings;
import git4idea.test.GitTestUtil;
import git4idea.ui.GitConvertFilesDialog;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.*;

/**
 * Tests converting file line separator to project code style separators on commit.
 * Defines 3 files with unix, windows and mac separator style and tries to commit them testing different git settings.
 * @author Kirill Likhodedov
 */
public class GitLineSeparatorsConverterTest extends GitTest {
  private GitVcsSettings mySettings;
  private ChangeListManagerImpl myChangeListManager;
  private String myCodeStyleSeparator;
  private TestDialogManager myDialogManager;

  private VirtualFile unixFile;
  private VirtualFile winFile;
  private VirtualFile macFile;

  @BeforeMethod
  @Override
  protected void setUp(Method testMethod) throws Exception {
    super.setUp(testMethod);
    myChangeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
    mySettings = GitVcsSettings.getInstance(myProject);
    myCodeStyleSeparator = CodeStyleFacade.getInstance(myProject).getLineSeparator();
    myDialogManager = GitTestUtil.registerDialogManager(myProject);

    unixFile = createFileInCommand("unix_file.txt", "Unix File\n");
    winFile = createFileInCommand("win_file.txt", "Windows File\r\n");
    macFile = createFileInCommand("mac_file.txt", "Mac File\r");
    myRepo.addCommit();
  }

  @Test
  public void testDoNotConvert() throws IOException, InvocationTargetException, InterruptedException {
    mySettings.setLineSeparatorsConversion(GitVcsSettings.ConversionPolicy.NONE);
    editAllFilesWithoutChangingSeparators();
    commit();
    assertOwnSeparators();
  }

  @Test
  public void testConvert() throws IOException, InvocationTargetException, InterruptedException {
    mySettings.setLineSeparatorsConversion(GitVcsSettings.ConversionPolicy.CONVERT);
    editAllFilesWithoutChangingSeparators();
    commit();
    assertSeparatorForAllFiles(myCodeStyleSeparator);
  }

  /**
   * If ASK USER option is set in the settings, a dialog on commit should be shown.
   * Test that pressing "Convert All" converts all files.
   */
  @Test
  public void testAskAndAnswerYes() throws InvocationTargetException, InterruptedException, IOException {
    final AtomicBoolean dialogShown = new AtomicBoolean();
    myDialogManager.registerDialogHandler(GitConvertFilesDialog.class, new TestDialogHandler<GitConvertFilesDialog>() {
      @Override
      public int handleDialog(GitConvertFilesDialog dialog) {
        dialogShown.set(true);
        return GitConvertFilesDialog.OK_EXIT_CODE;
      }
    });
    
    mySettings.setLineSeparatorsConversion(GitVcsSettings.ConversionPolicy.ASK);
    editAllFilesWithoutChangingSeparators();
    commit();
    assertTrue(dialogShown.get());
    assertSeparatorForAllFiles(myCodeStyleSeparator);
  }

  /**
   * If ASK USER option is set in the settings, a dialog on commit should be shown.
   * Test that pressing "Don't convert" doesn't convert files.
   */
  @Test
  public void testAskAndAnswerNo() throws IOException, InterruptedException, InvocationTargetException {
    final AtomicBoolean dialogShown = new AtomicBoolean();
    myDialogManager.registerDialogHandler(GitConvertFilesDialog.class, new TestDialogHandler<GitConvertFilesDialog>() {
      @Override
      public int handleDialog(GitConvertFilesDialog dialog) {
        dialogShown.set(true);
        return GitConvertFilesDialog.DO_NOT_CONVERT;
      }
    });

    mySettings.setLineSeparatorsConversion(GitVcsSettings.ConversionPolicy.ASK);
    editAllFilesWithoutChangingSeparators();
    commit();
    assertTrue(dialogShown.get());
    assertOwnSeparators();
  }

  /**
   * If ASK USER option is set in the settings, a dialog on commit should be shown.
   * Test that if several files are selected only they are converted.
   */
  @Test
  public void testAskAndSelectSomeFiles() throws IOException, InterruptedException, InvocationTargetException {
    final AtomicBoolean dialogShown = new AtomicBoolean();
    myDialogManager.registerDialogHandler(GitConvertFilesDialog.class, new TestDialogHandler<GitConvertFilesDialog>() {
      @Override
      public int handleDialog(GitConvertFilesDialog dialog) {
        try {
          // unchecking macFile
          // using reflection to avoid modifying the production code

          Field field = GitConvertFilesDialog.class.getDeclaredField("myFilesToConvert");
          field.setAccessible(true);
          CheckboxTreeBase tree = (CheckboxTreeBase)field.get(dialog); // tree of files shown in the dialog

          CheckedTreeNode macFileNode = getNodeForObject(tree, macFile); // find the node which shows the macFile with a checkbox

          // uncheck it
          Method method = CheckboxTreeBase.class.getDeclaredMethod("checkNode", CheckedTreeNode.class, Boolean.TYPE);
          method.setAccessible(true);
          method.invoke(tree, macFileNode, false);
          
          dialogShown.set(true);
        } catch (Exception e) {
          fail("", e);
        }
        return GitConvertFilesDialog.OK_EXIT_CODE;
      }
    });

    mySettings.setLineSeparatorsConversion(GitVcsSettings.ConversionPolicy.ASK);
    editAllFilesWithoutChangingSeparators();
    commit();

    assertTrue(dialogShown.get());
    assertSeparator(unixFile, myCodeStyleSeparator);
    assertSeparator(winFile, myCodeStyleSeparator);
    assertSeparator(macFile, "\r");
  }

  /**
   * In a tree finds a node which {@link com.intellij.ui.CheckedTreeNode#getUserObject() user object} is the given one.
   * Null is returned if nothing was found.
   */
  @Nullable
  private static CheckedTreeNode getNodeForObject(CheckboxTreeBase tree, Object userObject) {
    CheckedTreeNode root = (CheckedTreeNode)tree.getModel().getRoot();
    for (Enumeration en = root.breadthFirstEnumeration(); en.hasMoreElements(); ) {
      final Object o = en.nextElement();
      if (o instanceof CheckedTreeNode) {
        CheckedTreeNode node = (CheckedTreeNode) o;
        if (node.getUserObject().equals(userObject)) {
          return node;
        }
      }
    }
    return null;
  }

  /**
   * Commits via the ChangeListManager.
   */
  private void commit() throws InvocationTargetException, InterruptedException {
    myChangeListManager.ensureUpToDate(false);
    final LocalChangeList list = myChangeListManager.getDefaultChangeList();
    list.setComment("Sample message");
    GuiUtils.runOrInvokeAndWait(new Runnable() {
      @Override
      public void run() {
        myChangeListManager.commitChangesSynchronouslyWithResult(list, new ArrayList<Change>(list.getChanges()));
      }
    });
  }

  private void editAllFilesWithoutChangingSeparators() {
    editFileInCommand(unixFile, "newcontent\n");
    editFileInCommand(winFile, "newcontent\r\n");
    editFileInCommand(macFile, "newcontent\r");
  }

  private static void assertSeparator(VirtualFile file, String separator) throws IOException {
    String fileContent = new String(file.contentsToByteArray());
    String ending = fileContent.substring(fileContent.length() - separator.length());
    assertEquals(separator, ending);
  }

  private void assertSeparatorForAllFiles(String separator) throws IOException {
    assertSeparator(winFile, separator);
    assertSeparator(unixFile, separator);
    assertSeparator(macFile, separator);
  }

  private void assertOwnSeparators() throws IOException {
    assertSeparator(unixFile, "\n");
    assertSeparator(winFile, "\r\n");
    assertSeparator(macFile, "\r");
  }

}

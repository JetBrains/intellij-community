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

package org.jetbrains.android.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import org.jetbrains.android.AndroidTestCase;

public class AndroidCreateComponentTest extends AndroidTestCase {
  private static final String TEST_FOLDER = "createComponent";

  public void test1() {
    /*VirtualFile manifestFile = myFixture.findFileInTempDir(SdkConstants.FN_ANDROID_MANIFEST_XML);
    VirtualFile dir = manifestFile.getParent();
    final Project project = myFixture.getProject();
    final PsiDirectory psiDir = PsiManager.getInstance(project).findDirectory(dir);
    executeWritingCommand(new Runnable() {
      @Override
      public void run() {
        try {
          NewAndroidComponentDialog.doCreate(AndroidFileTemplateProvider.ACTIVITY, psiDir, project, "MyActivity", "", false);
          ApplicationManager.getApplication().saveAll();
          String expectedFileName = "f1.xml";
          myFixture.copyFileToProject(TEST_FOLDER + '/' + expectedFileName, expectedFileName);
          VirtualFile actual = myFixture.findFileInTempDir(SdkConstants.FN_ANDROID_MANIFEST_XML);
          VirtualFile expected = myFixture.findFileInTempDir(expectedFileName);
          IdeaTestUtil.assertFilesEqual(expected, actual);
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
    });*/
  }

  private void executeWritingCommand(final Runnable r) {
    CommandProcessor.getInstance().executeCommand(myFixture.getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(r);
      }
    }, "", null);
  }
}

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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import org.jetbrains.android.AndroidTestCase;

public class AndroidCreateComponentTest extends AndroidTestCase {
  private static final String TEST_FOLDER = "/createComponent/";

  public AndroidCreateComponentTest() {
    super(false);
  }

  public void testDeleteComponent() {
    myFixture.copyFileToProject(TEST_FOLDER + "f1.xml", "AndroidManifest.xml");
    final VirtualFile activityFile = myFixture.copyFileToProject(TEST_FOLDER + "MyActivity.java", "src/p1/p2/MyActivity.java");
    myFixture.configureFromExistingVirtualFile(activityFile);
    final PsiFile psiActivityFile = PsiManager.getInstance(getProject()).findFile(activityFile);
    final PsiClass activityClass = ((PsiJavaFile)psiActivityFile).getClasses()[0];
    final DataContext context = DataManager.getInstance().getDataContext(myFixture.getEditor().getComponent());
    new SafeDeleteHandler().invoke(getProject(), new PsiElement[]{activityClass}, context);
    myFixture.checkResultByFile("AndroidManifest.xml", TEST_FOLDER + "f1_after.xml", true);
  }
}

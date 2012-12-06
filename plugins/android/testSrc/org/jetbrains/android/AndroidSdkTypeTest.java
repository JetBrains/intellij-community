/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.android;

import com.intellij.ide.projectWizard.ProjectWizardTestCase;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.util.Condition;
import com.intellij.testFramework.TestActionEvent;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.util.AndroidBundle;

/**
 * @author Dmitry Avdeev
 *         Date: 12/5/12
 */
public class AndroidSdkTypeTest extends ProjectWizardTestCase {

  public void testUnsatisfied() throws Exception {
    ProjectSdksModel model = new ProjectSdksModel();
    AnAction action = getAddAction(model);
    try {
      action.actionPerformed(new TestActionEvent(action));
      fail("Exception should be thrown");
    }
    catch (Exception e) {
      assertEquals(AndroidSdkType.getInstance().getUnsatisfiedDependencyMessage(), e.getMessage());
    }
  }

  public void testSatisfied() throws Exception {
    ProjectSdksModel model = new ProjectSdksModel();
    model.addSdk(JavaSdkImpl.getMockJdk17());
    ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
    Sdk sdk = jdkTable.createSdk("a", AndroidSdkType.getInstance());
    mySdks.add(sdk);
    jdkTable.addJdk(sdk);
    AnAction action = getAddAction(model);
    try {
      action.actionPerformed(new TestActionEvent(action));
      fail("Exception should be thrown");
    }
    catch (Exception e) {
      assertEquals(AndroidBundle.message("cannot.parse.sdk.error"), e.getMessage());
    }
  }

  private static AnAction getAddAction(ProjectSdksModel model) {
    DefaultActionGroup group = new DefaultActionGroup();
    model.createAddActions(group, null, null, new Condition<SdkTypeId>() {
      @Override
      public boolean value(SdkTypeId id) {
        return id == AndroidSdkType.getInstance();
      }
    });
    AnAction[] children = group.getChildren(null);
    assertEquals(1, children.length);
    return children[0];
  }
}

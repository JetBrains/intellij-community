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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.dom.manifest.ApplicationComponent;
import org.jetbrains.android.dom.manifest.Service;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author coyote
 */
public class CreateServiceAction extends CreateComponentAction {
  public CreateServiceAction() {
    super(AndroidBundle.message("create.service.text"), AndroidBundle.message("create.service.description"));
  }

  @NotNull
  protected String getClassName() {
    return "android.app.Service";
  }

  protected ApplicationComponent addToManifest(@NotNull PsiClass aClass, @NotNull Application application) {
    Service service = application.addService();
    service.getServiceClass().setValue(aClass);
    return service;
  }

  protected String getErrorTitle() {
    return "Cannot create service";
  }

  protected String getCommandName() {
    return "Create Service";
  }

  protected String getActionName(PsiDirectory directory, String newName) {
    return "Creating service " + newName;
  }
}

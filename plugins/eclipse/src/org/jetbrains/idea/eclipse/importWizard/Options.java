// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.idea.eclipse.importWizard;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

public class Options {
  public @NonNls String commonModulesDirectory;
  public @NonNls String testPattern;
  public boolean reuseOutputPaths = false;

  public static Options defValue = new Options();


  public static final String ECLIPSE_REMOTE_PROJECT_STORAGE = "eclipse.remote.project.storage";
  public static String getProjectStorageDir(Project project){
    return PropertiesComponent.getInstance().getValue(ECLIPSE_REMOTE_PROJECT_STORAGE);
  }

  public static void saveProjectStorageDir(String dir) {
    PropertiesComponent.getInstance().setValue(ECLIPSE_REMOTE_PROJECT_STORAGE, dir);
  }
}
/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 11-Nov-2008
 */
package org.jetbrains.idea.eclipse.importWizard;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

public class Options {
  @NonNls
  public String commonModulesDirectory;
  @NonNls
  public String testPattern;
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
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

package org.jetbrains.plugins.groovy;

import com.intellij.openapi.roots.ContentEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.JarFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author peter
 */
public abstract class LightGroovyTestCase extends LightCodeInsightFixtureTestCase {
  public static final LightProjectDescriptor GROOVY_DESCRIPTOR = new LightProjectDescriptor() {
    public ModuleType getModuleType() {
      return StdModuleTypes.JAVA;
    }

    public Sdk getSdk() {
      return JavaSdkImpl.getMockJdk17("java 1.5");
    }

    public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
      final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary("GROOVY").getModifiableModel();
      final VirtualFile groovyJar =
        JarFileSystem.getInstance().refreshAndFindFileByPath(TestUtils.getMockJdkHome() + "/jre/lib/groovy-1.0.jar!/");
      modifiableModel.addRoot(groovyJar, OrderRootType.CLASSES);
      modifiableModel.commit();
    }
  };

  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return GROOVY_DESCRIPTOR;
  }

  /**
   * Return relative path to the test data. Path is relative to the
   * {@link com.intellij.openapi.application.PathManager#getHomePath()}
   *
   * @return relative path to the test data.
   */
  @NonNls
  protected abstract String getBasePath();

}

/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.junit4;

import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class JUnit4IntegrationTest extends JUnitAbstractIntegrationTest {

  public static final String CLASS_NAME = "a.Test1";
  private static final String METHOD_NAME = "simple";

  @Rule public final TestName myNameRule = new TestName();

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(createParams("4.12"),
                         createParams("4.11"),
                         createParams("4.10"),
                         createParams("4.9"),
                         createParams("4.8.2"),
                         createParams("4.5"),
                         createParams("4.4")
    );
  }

  private static Object[] createParams(final String version) {
    return new Object[]{version};
  }

  @Parameterized.Parameter
  public String myJUnitVersion;

  @Before
  public void before() {
    EdtTestUtil.runInEdtAndWait(() -> {
      setUp();
      Module module = createEmptyModule();
      String communityPath = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/');
      String methodName = myNameRule.getMethodName();
      methodName = methodName.substring(0, methodName.indexOf("["));
      String testDataPath = communityPath + File.separator + "plugins" + File.separator + "junit5_rt_tests" +
                            File.separator + "testData" + File.separator + "integration" + File.separator + methodName;
      
      addLibs(module, new JpsMavenRepositoryLibraryDescriptor("junit", "junit", myJUnitVersion), getRepoManager());

      ModuleRootModificationUtil.setModuleSdk(module, JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk());
      ModuleRootModificationUtil.updateModel(module, model -> {
        ContentEntry contentEntry = model.addContentEntry(VfsUtilCore.pathToUrl(testDataPath));
        contentEntry.addSourceFolder(VfsUtilCore.pathToUrl(testDataPath + File.separator + "test"), true);
        CompilerModuleExtension moduleExtension = model.getModuleExtension(CompilerModuleExtension.class);
        moduleExtension.inheritCompilerOutputPath(false);
        moduleExtension.setCompilerOutputPathForTests(VfsUtilCore.pathToUrl(testDataPath + File.separator + "out"));
      });
    });
  }

  @After
  public void after() {
    EdtTestUtil.runInEdtAndWait(() -> {
      tearDown();
    });
  }

  @Override
  public String getName() {
    return myNameRule.getMethodName();
  }

  @Test
  public void ignoredTestMethod() {
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiClass psiClass = findClass(getModule1(), CLASS_NAME);
      assertNotNull(psiClass);
      PsiMethod testMethod = psiClass.findMethodsByName(METHOD_NAME, false)[0];
      JUnitConfiguration configuration = createConfiguration(testMethod);
      ProcessOutput processOutput = doStartTestsProcess(configuration);
      String testOutput = processOutput.out.toString();
      assertEmpty(processOutput.err);
      switch (myJUnitVersion) {
        case "4.4": case "4.5": break; //shouldn't work for old versions
        default:
          assertTrue(testOutput, testOutput.contains("Test1"));
      }
    });
  }


}

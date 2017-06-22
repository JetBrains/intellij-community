/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.idea.IdeaTestApplication;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataProvider;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.TestFailed;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JUnit5IntegrationTest extends JUnitAbstractCompilingIntegrationTest {

  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/junit5_rt_tests/testData/integration/mixed45Project");
  }

  public void testRunPackage() throws Exception {
    PsiPackage aPackage = JavaPsiFacade.getInstance(myProject).findPackage("junit");
    assertNotNull(aPackage);
    RunConfiguration configuration = createConfiguration(aPackage);
    assertInstanceOf(configuration, JUnitConfiguration.class);
    List<String> sys = new ArrayList<>();
    List<String> out = new ArrayList<>();
    List<String> err = new ArrayList<>();
    List<ServiceMessage> messages = new ArrayList<>();
    doStartTestsProcess(configuration, out, err, sys, messages);
    assertEmpty(out);
    assertEmpty(err);
    assertSize(2, messages.stream().filter(message -> message instanceof TestFailed).collect(Collectors.toList()));
  }

  public void testSelectedMethods() throws Exception {
    final IdeaTestApplication testApplication = IdeaTestApplication.getInstance();
    try {
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
      GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
      PsiElement[] elements = new PsiElement[]{
        psiFacade.findClass("junit.v4.MyTest4", scope).getMethods()[0],
        psiFacade.findClass("junit.v5.MyTest5", scope).getMethods()[0]
      };
      testApplication.setDataProvider(new TestDataProvider(myProject) {
        @Override
        public Object getData(@NonNls String dataId) {
          if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
            return elements;
          }
          return super.getData(dataId);
        }
      });
      MapDataContext dataContext = new MapDataContext();
      
      dataContext.put(LangDataKeys.PSI_ELEMENT_ARRAY, elements);
      dataContext.put(CommonDataKeys.PROJECT, myProject);
      ConfigurationContext fromContext = ConfigurationContext.getFromContext(dataContext);
      assertNotNull(fromContext);

      RunConfiguration configuration = fromContext.getConfiguration().getConfiguration();
      assertNotNull(configuration);

      ArrayList<String> out = new ArrayList<>();
      ArrayList<String> err = new ArrayList<>();
      ArrayList<String> sys = new ArrayList<>();
      ArrayList<ServiceMessage> messages = new ArrayList<>();

      doStartTestsProcess(configuration, out, err, sys, messages);

      assertTrue(sys.toString().contains("-junit5"));
      assertEmpty(err);
      assertEmpty(out);

      assertSize(2, messages.stream().filter(message -> message instanceof TestFailed).collect(Collectors.toList()));
    }
    finally {
      testApplication.setDataProvider(null);
    }
  }

  public void testJUnit4MethodRunWithJUnit4Runner() throws Exception {
    PsiClass testClass = JavaPsiFacade.getInstance(myProject).findClass("junit.v4.MyTest4", GlobalSearchScope.projectScope(myProject));
    assertNotNull(testClass);
    RunConfiguration configuration = createConfiguration(testClass.getMethods()[0]);
    assertInstanceOf(configuration, JUnitConfiguration.class);
    List<String> sys = new ArrayList<>();
    List<String> out = new ArrayList<>();
    List<String> err = new ArrayList<>();
    List<ServiceMessage> messages = new ArrayList<>();
    doStartTestsProcess(configuration, out, err, sys, messages);
    String systemOutput = sys.toString(); //command line
    
    //check running with junit 4
    assertTrue(systemOutput.contains("-junit4"));
    assertFalse(systemOutput.contains("-junit5"));
    
    assertEmpty(out);
    assertEmpty(err);
    assertSize(1, messages.stream().filter(message -> message instanceof TestFailed).collect(Collectors.toList()));
  }

  @Override
  protected JpsMavenRepositoryLibraryDescriptor[] getRequiredLibs() {
    return new JpsMavenRepositoryLibraryDescriptor[] {
      new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.0.0-M4"),
      new JpsMavenRepositoryLibraryDescriptor("junit", "junit", "4.12")
    };
  }
}

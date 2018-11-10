// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.execution.testDiscovery.TestDiscoveryExtension;
import com.intellij.execution.testDiscovery.TestDiscoveryProducer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestDataGuessByTestDiscoveryUtil {
  public static final String COMMUNITY_PREFIX = "/community";
  private static final Logger LOG = Logger.getInstance(TestDataGuessByTestDiscoveryUtil.class);

  @NotNull
  static List<String> collectTestDataByExistingFiles(@NotNull PsiMethod method) {
    if (!(Registry.is(TestDiscoveryExtension.TEST_DISCOVERY_REGISTRY_KEY) || ApplicationManager.getApplication().isInternal())) {
      return Collections.emptyList();
    }
    PsiClass testClass = method.getContainingClass();
    if (testClass == null) return Collections.emptyList();
    String testClassQualifiedName = testClass.getQualifiedName();
    if (testClassQualifiedName == null) return Collections.emptyList();
    List<String> testQName = Collections.singletonList(testClassQualifiedName + "." + method.getName());
    try {
      List<String> testData = new ArrayList<>();
      Project project = method.getProject();
      String basePath = project.getBasePath();
      TestDiscoveryProducer.consumeAffectedPaths(project, testQName, path -> {

        //TODO for those strange people with community sources
        String fullPath = basePath + path;
        if (FileUtil.exists(fullPath)) {
          testData.add(fullPath);
          return;
        }
        path = StringUtil.trimStart(path, COMMUNITY_PREFIX);
        fullPath = basePath + path;
        if (FileUtil.exists(fullPath)) {
          testData.add(fullPath);
        }
      }, (byte)0x0 /* TODO */);

      return testData;
    }
    catch (IOException e) {
      LOG.error(e);
      return Collections.emptyList();
    }
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.execution.testDiscovery.TestDiscoveryExtension;
import com.intellij.execution.testDiscovery.TestDiscoveryProducer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestDataGuessByTestDiscoveryUtil {
  public static final String COMMUNITY_PREFIX = "/community";
  private static final Logger LOG = Logger.getInstance(TestDataGuessByTestDiscoveryUtil.class);

  @NotNull
  static List<TestDataFile> collectTestDataByExistingFiles(@NotNull PsiMethod method) {
    if (!isEnabled()) return Collections.emptyList();
    PsiClass testClass = ReadAction.compute(() -> method.getContainingClass());
    if (testClass == null) return Collections.emptyList();
    String testClassQualifiedName = ReadAction.compute(() -> testClass.getQualifiedName());
    if (testClassQualifiedName == null) return Collections.emptyList();
    List<Couple<String>> testQName =
      Collections.singletonList(Couple.of(testClassQualifiedName, ReadAction.compute(() -> method.getName())));
    try {
      Project project = ReadAction.compute(() -> method.getProject());
      AffectedPathConsumer consumer = new AffectedPathConsumer(project);
      TestDiscoveryProducer.consumeAffectedPaths(project, testQName, consumer, (byte)0x0 /* TODO */);
      return consumer.getTestData();
    }
    catch (IOException e) {
      LOG.error(e);
      return Collections.emptyList();
    }
  }

  @NotNull
  static List<TestDataFile> collectTestDataByExistingFiles(@NotNull PsiClass parametrizedTestClass) {
    if (!isEnabled()) return Collections.emptyList();
    String testClassQualifiedName = ReadAction.compute(() -> parametrizedTestClass.getQualifiedName());
    if (testClassQualifiedName == null) return Collections.emptyList();
    Project project = ReadAction.compute(() -> parametrizedTestClass.getProject());
    try {
      AffectedPathConsumer consumer = new AffectedPathConsumer(project);
      TestDiscoveryProducer.consumeAffectedPaths(project, testClassQualifiedName, consumer, (byte)0x0 /* TODO */);
      return consumer.getTestData();
    }
    catch (IOException e) {
      LOG.error(e);
      return Collections.emptyList();
    }
  }

  private static boolean isEnabled() {
    return Registry.is(TestDiscoveryExtension.TEST_DISCOVERY_REGISTRY_KEY) || ApplicationManager.getApplication().isInternal();
  }

  private static class AffectedPathConsumer implements Consumer<String> {
    private final List<String> myTestData = new ArrayList<>();
    private final String myBasePath;

    private AffectedPathConsumer(Project project) {myBasePath = project.getBasePath();}

    @Override
    public void consume(String path) {
      //TODO for those strange people with community sources
      String fullPath = myBasePath + path;
      if (FileUtil.exists(fullPath)) {
        myTestData.add(fullPath);
        return;
      }
      path = StringUtil.trimStart(path, COMMUNITY_PREFIX);
      fullPath = myBasePath + path;
      if (FileUtil.exists(fullPath)) {
        myTestData.add(fullPath);
      }
    }

    @NotNull
    List<TestDataFile> getTestData() {
      return ContainerUtil.mapNotNull(myTestData, f -> new TestDataFile.LazyResolved(f));
    }
  }

}

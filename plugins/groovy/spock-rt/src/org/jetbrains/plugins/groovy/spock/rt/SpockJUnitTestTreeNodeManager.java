// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.spock.rt;

import com.intellij.junit4.JUnitTestTreeNodeManager;
import org.junit.runner.Description;
import org.spockframework.runtime.model.FeatureMetadata;

import java.lang.annotation.Annotation;
import java.util.Collection;

public class SpockJUnitTestTreeNodeManager implements JUnitTestTreeNodeManager {

  @Override
  public TestNodePresentation getRootNodePresentation(String fullName) {
    return JUnitTestTreeNodeManager.JAVA_NODE_NAMES_MANAGER.getRootNodePresentation(fullName);
  }

  @Override
  public String getNodeName(String fqName, boolean splitBySlash) {
    return JUnitTestTreeNodeManager.JAVA_NODE_NAMES_MANAGER.getNodeName(fqName, splitBySlash);
  }

  @Override
  public String getTestLocation(Description description, String className, String methodName) {
    Collection<? extends Annotation> annotations = description.getAnnotations();
    String spockAwareName = methodName;
    for (Annotation annotation : annotations) {
      if (annotation instanceof FeatureMetadata) {
        spockAwareName = ((FeatureMetadata)annotation).name();
      }
    }
    return JUnitTestTreeNodeManager.JAVA_NODE_NAMES_MANAGER.getTestLocation(description, className, spockAwareName);
  }
}

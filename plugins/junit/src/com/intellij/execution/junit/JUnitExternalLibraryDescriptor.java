/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.junit;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author nik
 */
public abstract class JUnitExternalLibraryDescriptor extends ExternalLibraryDescriptor {
  private static final Logger LOG = Logger.getInstance(JUnitExternalLibraryDescriptor.class);
  public static final ExternalLibraryDescriptor JUNIT3 = new JUnitExternalLibraryDescriptor("3") {
    @NotNull
    @Override
    public List<String> getLibraryClassesRoots() {
      return Collections.singletonList(JavaSdkUtil.getJunit3JarPath());
    }
  };
  public static final ExternalLibraryDescriptor JUNIT4 = new JUnitExternalLibraryDescriptor("4") {
    @NotNull
    @Override
    public List<String> getLibraryClassesRoots() {
      return JavaSdkUtil.getJUnit4JarPaths();
    }
  };
  public static final ExternalLibraryDescriptor JUNIT5 = new JUnitExternalLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5") {
    @NotNull
    @Override
    public List<String> getLibraryClassesRoots() {
      return Stream.of("org.junit.jupiter.api.Test", "org.opentest4j.AssertionFailedError", 
                       "org.apiguardian.api.API", "org.junit.platform.commons.JUnitException")
        .map(className -> {
          try {
            return PathUtil.getJarPathForClass(Class.forName(className));
          }
          catch (ClassNotFoundException e) {
            LOG.info(e);
          }
          return null;
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    }
  };
  private final String myVersion;

  private JUnitExternalLibraryDescriptor(String version) {
    this("junit", "junit", version);
  }

  private JUnitExternalLibraryDescriptor(final String groupId, final String artifactId, final String version) {
    super(groupId, artifactId, version + ".0", version + ".999");
    myVersion = version;
  }

  @Override
  public String getPresentableName() {
    return "JUnit" + myVersion;
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5.report;

import com.intellij.rt.execution.junit.MapSerializerUtil;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.*;
import org.junit.platform.launcher.TestIdentifier;

import java.io.File;
import java.util.Optional;

final public class LocationInfo {
  static final LocationInfo EMPTY = new LocationInfo(null, null, null);

  private final String locationHint;
  private final String metainfo;

  public LocationInfo(TestSource opt, TestSource parent, TestIdentifier node) {
    this.locationHint = getLocationHintValue(opt, parent);
    if (this.locationHint.isEmpty()) {
      this.metainfo = null;
    } else {
      this.metainfo = getMetaInfoValue(node);
    }
  }

  public String locationHint() {
    return locationHint;
  }

  public String metainfo() {
    return metainfo;
  }

  public static LocationInfo compute(TestIdentifier node, TestIdentifier parent) {
    TestSource parentSource = parent != null ? parent.getSource().orElse(null) : null;
    Optional<TestSource> sourceOpt = node.getSource();
    //noinspection OptionalIsPresent
    if (!sourceOpt.isPresent()) return EMPTY;

    return new LocationInfo(sourceOpt.get(), parentSource, node);
  }

  private static String getMetaInfoValue(TestIdentifier root) {
    if (root == null || !root.getSource().isPresent()) return null;

    TestSource source = root.getSource().get();
    if (source instanceof MethodSource) {
      return ((MethodSource)source).getMethodParameterTypes();
    }
    else if (source instanceof ClassSource) {
      Optional<FilePosition> position = ((ClassSource)source).getPosition();
      if (!position.isPresent()) return null;

      FilePosition pos = position.get();
      return (pos.getLine() - 1) + ":" + (pos.getColumn().orElse(1) - 1);
    }
    else {
      return null;
    }
  }

  private static String getLocationHintValue(TestSource testSource, TestSource parentSource) {
    if (testSource instanceof CompositeTestSource) {
      CompositeTestSource composite = (CompositeTestSource)testSource;
      for (TestSource s : composite.getSources()) {
        String v = getLocationHintValue(s, parentSource);
        if (!v.isEmpty()) return v;
      }
      return "";
    }

    if (testSource instanceof FileSource) {
      FileSource fileSource = (FileSource)testSource;
      File file = fileSource.getFile();
      String line = fileSource.getPosition()
        .map(position -> ":" + position.getLine())
        .orElse("");
      return "file://" + file.getAbsolutePath() + line;
    }

    if (testSource instanceof MethodSource) {
      MethodSource methodSource = (MethodSource)testSource;
      return javaLocation(methodSource.getClassName(), methodSource.getMethodName(), true);
    }

    if (testSource instanceof ClassSource) {
      String className = ((ClassSource)testSource).getClassName();
      return javaLocation(className, null, false);
    }

    if (parentSource != null) {
      return getLocationHintValue(parentSource, null);
    }

    return "";
  }

  private static String javaLocation(String className, String maybeMethodName, boolean isTest) {
    String type = isTest ? "test" : "suite";
    String methodName = maybeMethodName == null ? "" : "/" + maybeMethodName;
    return "java:" + type + "://" + MapSerializerUtil.escapeStr(className + methodName, MapSerializerUtil.STD_ESCAPER);
  }
}

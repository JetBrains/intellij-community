// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.mock;

import com.intellij.util.xmlb.annotations.XCollection;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AnotherFacetConfigProperties {
  @XCollection(propertyElementName = "firstElement", elementName = "field", valueAttributeName = "")
  public List<String> firstElement = Arrays.asList("gradle");

  @XCollection(propertyElementName = "secondElement", elementName = "field", valueAttributeName = "")
  public List<String> secondElement = Collections.singletonList("maven");
}


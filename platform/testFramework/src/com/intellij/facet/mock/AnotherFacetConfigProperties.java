// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.mock;

import com.intellij.util.xmlb.annotations.XCollection;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AnotherFacetConfigProperties {
  @XCollection(propertyElementName = "firstElement", elementName = "field", valueAttributeName = "")
  public List<String> firstElement = Arrays.asList("gradle");

  @XCollection(propertyElementName = "secondElement", elementName = "field", valueAttributeName = "")
  public List<String> secondElement = Collections.singletonList("maven");
}


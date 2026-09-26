// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

@Tag("configuration")
public class ConfigurationBean {
  @Attribute("name")
  public String name;

  @Attribute("configurationId")
  public String configurationId;
}

// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

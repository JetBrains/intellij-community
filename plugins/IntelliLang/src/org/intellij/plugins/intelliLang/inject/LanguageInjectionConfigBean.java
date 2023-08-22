// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang.inject;

import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author gregsh
 */
public final class LanguageInjectionConfigBean {
  @Attribute("config")
  public String myConfigUrl;

  public String getConfigUrl() {
    return myConfigUrl;
  }
}


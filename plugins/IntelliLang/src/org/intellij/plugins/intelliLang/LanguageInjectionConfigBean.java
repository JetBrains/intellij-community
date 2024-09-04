// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang;

import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author gregsh
 */
final class LanguageInjectionConfigBean {
  @Attribute("config")
  public String myConfigUrl;

  public String getConfigUrl() {
    return myConfigUrl;
  }
}


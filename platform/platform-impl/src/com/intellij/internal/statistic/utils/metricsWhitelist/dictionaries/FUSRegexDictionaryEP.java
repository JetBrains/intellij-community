// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.metricsWhitelist.dictionaries;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * Dictionaries registered via this extension point are downloaded from the remote server and cached on IDE startup.
 */
public class FUSRegexDictionaryEP extends AbstractExtensionPointBean {
  public static final ExtensionPointName<FUSRegexDictionaryEP> EP_NAME =
    ExtensionPointName.create("com.intellij.statistics.metricsWhitelist.regexDictionary");

  @Attribute("id")
  public String id;
}

/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.util;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nullable;

/**
 * @author gregsh
 */
public class TipAndTrickBean extends AbstractExtensionPointBean {
  public static final ExtensionPointName<TipAndTrickBean> EP_NAME = ExtensionPointName.create("com.intellij.tipAndTrick");

  @Attribute("file")
  public String fileName;

  @Attribute("feature-id")
  public String featureId;

  @Nullable
  public static TipAndTrickBean findByFileName(String tipFileName) {
    for (TipAndTrickBean tip : Extensions.getExtensions(EP_NAME)) {
      if (Comparing.equal(tipFileName, tip.fileName)) {
        return tip;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return "TipAndTrickBean{" +
           "fileName='" + fileName + '\'' +
           ", plugin='" + getPluginDescriptor().getPluginId() + '\'' +
           '}';
  }
}


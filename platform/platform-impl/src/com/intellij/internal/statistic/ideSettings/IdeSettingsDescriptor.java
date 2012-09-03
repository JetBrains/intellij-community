/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.internal.statistic.ideSettings;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@Tag("setting")
public class IdeSettingsDescriptor {
  @Attribute("persistent-component")
  public String myProviderName;

  @Attribute("properties")
  public String myPropertyNames;

  @NotNull
  public List<String> getPropertyNames() {
    return myPropertyNames == null ? Collections.<String>emptyList() : StringUtil.split(myPropertyNames, ",");
  }
}

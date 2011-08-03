/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.inspections;

import com.intellij.util.xml.Converter;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.WrappingConverter;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import org.jetbrains.android.dom.AndroidDomElement;
import org.jetbrains.android.dom.converters.AndroidPackageConverter;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class AndroidDomInspection extends BasicDomElementsInspection<AndroidDomElement> {
  public AndroidDomInspection() {
    super(AndroidDomElement.class);
  }

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return AndroidBundle.message("android.inspections.group.name");
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return AndroidBundle.message("android.inspections.dom.name");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "AndroidDomInspection";
  }

  @Override
  protected boolean shouldCheckResolveProblems(GenericDomValue value) {
    final Converter realConverter = WrappingConverter.getDeepestConverter(value.getConverter(), value);
    return !(realConverter instanceof AndroidPackageConverter);
  }
}
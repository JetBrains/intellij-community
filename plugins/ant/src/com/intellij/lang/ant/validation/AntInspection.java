/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant.validation;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.dom.AntDomProject;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public abstract class AntInspection extends BasicDomElementsInspection<AntDomProject> {

  protected AntInspection() {
    super(AntDomProject.class);
  }

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return AntBundle.message("ant.inspections.display.name");
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  public boolean isEnabledByDefault() {
    return true;
  }
}

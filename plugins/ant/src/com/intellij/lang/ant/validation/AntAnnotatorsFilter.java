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
package com.intellij.lang.ant.validation;

import com.intellij.lang.ExternalAnnotatorsFilter;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.ant.dom.AntDomFileDescription;
import com.intellij.lang.xml.XMLExternalAnnotator;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;

/**
 * @author Dmitry Avdeev
 */
public class AntAnnotatorsFilter implements ExternalAnnotatorsFilter {
  @Override
  public boolean isProhibited(ExternalAnnotator annotator, PsiFile file) {
    return annotator instanceof XMLExternalAnnotator &&
           file instanceof XmlFile &&
           ReadAction.compute(() -> AntDomFileDescription.isAntFile((XmlFile)file));
  }
}

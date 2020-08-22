/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.containers.JBIterable;
import com.intellij.xml.util.ColorSampleLookupValue;
import com.intellij.xml.util.UserColorLookup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;

public class JavaFxColorReference extends PsiReferenceBase<XmlAttributeValue> {
  public JavaFxColorReference(XmlAttributeValue value) {
    super(value);
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    final Project project = getElement().getProject();
    final PsiClass psiClass =
      JavaPsiFacade.getInstance(project).findClass(JavaFxCommonNames.JAVAFX_SCENE_COLOR, GlobalSearchScope.allScope(project));
    if (psiClass != null) {
      return psiClass.findFieldByName(StringUtil.toUpperCase(getCanonicalText()), false);
    }
    return null;
  }

  @Override
  public LookupElement @NotNull [] getVariants() {
    return JBIterable
      .of(ColorSampleLookupValue.getColors())
      .map(color -> new ColorSampleLookupValue(color.getName(), color.getValue(), true).toLookupElement())
      .append(new UserColorLookup())
      .toArray(LookupElement.EMPTY_ARRAY);
  }

  @Override
  public boolean isSoft() {
    return true;
  }
}

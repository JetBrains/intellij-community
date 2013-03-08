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

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.xml.util.ColorSampleLookupValue;
import com.intellij.xml.util.UserColorLookup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonClassNames;

/**
 * User: anna
 * Date: 3/7/13
 */
public class JavaFxColorReference extends PsiReferenceBase<XmlAttributeValue> {
  public JavaFxColorReference(XmlAttributeValue value) {
    super(value);
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    final Project project = getElement().getProject();
    final PsiClass psiClass =
      JavaPsiFacade.getInstance(project).findClass(JavaFxCommonClassNames.JAVAFX_SCENE_COLOR, GlobalSearchScope.allScope(project));
    if (psiClass != null) {
      return psiClass.findFieldByName(getCanonicalText().toUpperCase(), false);
    }
    return null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    final ColorSampleLookupValue[] lookupValues = ColorSampleLookupValue.getColors();
    final Object[] vars = new Object[lookupValues.length + 1];
    for (int i = 0; i < lookupValues.length; i++) {
      final ColorSampleLookupValue value = lookupValues[i];
      vars[i] = new ColorSampleLookupValue(value.getName(), value.getValue(), true);
    }
    vars[lookupValues.length] = new UserColorLookup();
    return vars;
  }

  @Override
  public boolean isSoft() {
    return true;
  }
}

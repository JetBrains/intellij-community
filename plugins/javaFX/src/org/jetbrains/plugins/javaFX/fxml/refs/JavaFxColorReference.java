// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public final class JavaFxColorReference extends PsiReferenceBase<XmlAttributeValue> {
  public JavaFxColorReference(XmlAttributeValue value) {
    super(value);
  }

  @Override
  public @Nullable PsiElement resolve() {
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

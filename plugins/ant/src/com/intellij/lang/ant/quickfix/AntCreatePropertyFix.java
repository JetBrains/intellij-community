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
package com.intellij.lang.ant.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.misc.AntPsiUtil;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.impl.AntFileImpl;
import com.intellij.lang.ant.psi.impl.reference.AntPropertyReference;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;

public class AntCreatePropertyFix extends BaseIntentionAction {

  private final AntPropertyReference myRef;
  private final PropertiesFile myPropFile;

  public AntCreatePropertyFix(final AntPropertyReference ref) {
    this(ref, null);
  }

  public AntCreatePropertyFix(final AntPropertyReference ref, final PropertiesFile propFile) {
    myRef = ref;
    myPropFile = propFile;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @NotNull
  public String getFamilyName() {
    final String i18nName = AntBundle.message("intention.create.property.family.name");
    return (i18nName == null) ? "Create property" : i18nName;
  }

  @NotNull
  public String getText() {
    StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(getFamilyName());
      builder.append(" '");
      builder.append(myRef.getCanonicalRepresentationText());
      builder.append('\'');
      if (myPropFile != null) {
        builder.append(' ');
        builder.append(AntBundle.message("text.in.the.file", myPropFile.getName()));
      }
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final String name = myRef.getCanonicalRepresentationText();
    if( name == null) return;
    
    Navigatable result = null;
    if (myPropFile != null) {
      result = (Navigatable)myPropFile.addProperty(PropertiesElementFactory.createProperty(myPropFile.getProject(), name, ""));
    }
    else {
      final AntElement element = myRef.getElement();
      final AntElement anchor = AntPsiUtil.getSubProjectElement(element);
      final XmlTag projectTag = element.getAntProject().getSourceElement();
      XmlTag propTag = projectTag.createChildTag(AntFileImpl.PROPERTY, projectTag.getNamespace(), null, false);
      propTag.setAttribute(AntFileImpl.NAME_ATTR, name);
      if (CodeInsightUtilBase.preparePsiElementForWrite(projectTag)) {
        result = (Navigatable)((anchor == null) ? projectTag.add(propTag) : projectTag.addBefore(propTag, anchor.getSourceElement()));
      }
    }
    if (result != null) {
      result.navigate(true);
    }
  }
}

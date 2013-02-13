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
package org.jetbrains.plugins.javaFX.css.refs;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.XmlAttributeValuePattern;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.*;
import com.intellij.psi.css.impl.util.references.CssClassOrIdReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.refs.FxmlReferencesContributor;

import java.util.Set;

/**
 * User: anna
 * Date: 2/13/13
 */
public class JavaFxCSSReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    final XmlAttributeValuePattern attributeValueInFxml = XmlPatterns.xmlAttributeValue().with(FxmlReferencesContributor.inFxmlCondition());
    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue().withParent(XmlPatterns.xmlAttribute()
                                                                                     .withName(FxmlConstants.STYLE_CLASS))
                                          .and(attributeValueInFxml), new JavaFxCssReferenceProvider());
  }

  private static class JavaFxCssReferenceProvider extends PsiReferenceProvider {
    @NotNull
    @Override
    public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                                 @NotNull ProcessingContext context) {
      return new PsiReference[]{new JavaFxCssReference(element)};
    }
  }

  private static class JavaFxCssReference extends CssClassOrIdReference {

    private Set<VirtualFile> myCssFiles = new HashSet<VirtualFile>();

    public JavaFxCssReference(PsiElement element) {
      super(element, null, true, true);
      final XmlTag xmlTag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
      if (xmlTag != null) {
        XmlTag parentTag = xmlTag;
        while ((parentTag = parentTag.getParentTag()) != null) {
          XmlAttribute stylesheet = parentTag.getAttribute(FxmlConstants.STYLESHEETS);
          if (stylesheet != null) {
            collectStyleSheets(stylesheet);
          }
          else {
            final XmlTag[] tags = parentTag.findSubTags(FxmlConstants.STYLESHEETS);
            for (XmlTag tag : tags) {
              final XmlTag[] urlTags = tag.findSubTags("URL");
              for (XmlTag urlTag : urlTags) {
                stylesheet = urlTag.getAttribute(FxmlConstants.VALUE);
                if (stylesheet != null) {
                  collectStyleSheets(stylesheet);
                }
              }
            }
          }
        }
      }
    }

    private void collectStyleSheets(XmlAttribute stylesheet) {
      final XmlAttributeValue valueElement = stylesheet.getValueElement();
      if (valueElement != null) {
        final PsiReference[] references = valueElement.getReferences();
        for (PsiReference reference : references) {
          final PsiElement stylesheetFile = reference.resolve();
          if (stylesheetFile != null) {
            myCssFiles.add(PsiUtilCore.getVirtualFile(stylesheetFile.getContainingFile()));
          }
        }
      }
    }

    @Override
    protected boolean hasExplicitIdMark() {
      return false;
    }

    @Override
    protected boolean isId() {
      return false;
    }

    @Override
    public boolean isSoft() {
      return false;
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
      if (!myCssFiles.contains(PsiUtilCore.getVirtualFile(element.getContainingFile()))) return false;
      final String canonicalText = getCanonicalText();
      final String name = ((PsiNamedElement)element).getName();
      return Comparing.strEqual(canonicalText, name);
    }

    @Override
    protected GlobalSearchScope getSearchScope() {
      return GlobalSearchScope.filesScope(getElement().getProject(), myCssFiles);
    }
  }
}

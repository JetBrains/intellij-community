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
package org.jetbrains.plugins.javaFX.fxml.codeInsight;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.icons.AllIcons;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ConstantFunction;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;

/**
 * User: anna
 */
public class JavaFxRelatedItemLineMarkerProvider extends RelatedItemLineMarkerProvider {
  @Override
  protected void collectNavigationMarkers(@NotNull PsiElement element, final Collection<? super RelatedItemLineMarkerInfo> result) {
    if (element instanceof PsiField) {
      final PsiField field = (PsiField)element;
      if (JavaFxPsiUtil.isVisibleInFxml(field) && !field.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiClass containingClass = field.getContainingClass();
        if (containingClass != null && containingClass.hasModifierProperty(PsiModifier.PUBLIC)) {
          final ArrayList<GotoRelatedItem> targets = new ArrayList<GotoRelatedItem>();
          collectTargets(element, targets, new Function<PsiElement, GotoRelatedItem>() {
            @Override
            public GotoRelatedItem fun(PsiElement element) {
              return new GotoRelatedItem(element);
            }
          }, true);
          if (targets.isEmpty()) return;

          result.add(new RelatedItemLineMarkerInfo<PsiField>(field, field.getNameIdentifier().getTextRange(),
                                                             AllIcons.FileTypes.Xml, Pass.UPDATE_OVERRIDEN_MARKERS,  null,
                                                             new JavaFXIdIconNavigationHandler(),  GutterIconRenderer.Alignment.LEFT,
                                                             targets));
        }
      }
    }
  }

  private static <T> void collectTargets(PsiElement element, final ArrayList<T> targets, final Function<PsiElement, T> fun, final boolean stopAtFirst) {
    ReferencesSearch.search(element).forEach(new Processor<PsiReference>() {
      @Override
      public boolean process(PsiReference reference) {
        final PsiElement referenceElement = reference.getElement();
        if (referenceElement == null) return true;
        if (!(referenceElement instanceof XmlAttributeValue)) return true;
        final XmlAttributeValue attributeValue = (XmlAttributeValue)referenceElement;
        final PsiElement parent = attributeValue.getParent();
        if (!(parent instanceof XmlAttribute)) return true;
        final PsiFile containingFile = referenceElement.getContainingFile();
        if (containingFile == null) return true;
        if (JavaFxFileTypeFactory.isFxml(containingFile)) {
          targets.add(fun.fun(parent));
          if (stopAtFirst) return false;
        }
        return true;
      }
    });
  }

  private static class JavaFXIdIconNavigationHandler implements GutterIconNavigationHandler<PsiField> {
    @Override
    public void navigate(MouseEvent e, PsiField field) {
      final ArrayList<PsiElement> relatedItems = new ArrayList<PsiElement>();
      collectTargets(field, relatedItems, Function.ID, false);
      if (relatedItems.size() == 1) {
        NavigationUtil.activateFileWithPsiElement(relatedItems.get(0));
        return;
      }
      final JBPopup popup = NavigationUtil
        .getPsiElementPopup(relatedItems.toArray(new PsiElement[relatedItems.size()]), "<html>Choose component with fx:id <b>" + field.getName() + "<b></html>");
      popup.show(new RelativePoint(e));
    }
  }
}

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
package org.jetbrains.plugins.groovy.codeInsight;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.JavaLineMarkerProvider;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;

import java.util.Collection;
import java.util.List;

/**
 * @author ilyas
 * Same logic as for Java LMP
 */
public class GroovyLineMarkerProvider extends JavaLineMarkerProvider{
  public GroovyLineMarkerProvider(DaemonCodeAnalyzerSettings daemonSettings, EditorColorsManager colorsManager) {
    super(daemonSettings, colorsManager);
  }

  @Override
  public LineMarkerInfo getLineMarkerInfo(final PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiNameIdentifierOwner) {
      final ASTNode node = element.getNode();
      if (node != null && TokenSets.PROPERTY_NAMES.contains(node.getElementType())) {
        return super.getLineMarkerInfo(((PsiNameIdentifierOwner)parent).getNameIdentifier());
      }
    }
    return super.getLineMarkerInfo(element);
  }

  @Override
  public void collectSlowLineMarkers(final List<PsiElement> elements, final Collection<LineMarkerInfo> result) {
    super.collectSlowLineMarkers(elements, result);
  }
}

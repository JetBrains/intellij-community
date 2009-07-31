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

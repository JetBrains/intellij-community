package org.jetbrains.plugins.groovy.codeInsight;

import com.intellij.codeInsight.daemon.impl.JavaLineMarkerProvider;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.psi.PsiElement;

import java.util.Collection;
import java.util.List;

/**
 * @author ilyas
 * Same logic as for Java LMP
 */
public class GroovyLineMarkerProvider extends JavaLineMarkerProvider{
  @Override
  public LineMarkerInfo getLineMarkerInfo(final PsiElement element) {
    return super.getLineMarkerInfo(element);
  }

  @Override
  public void collectSlowLineMarkers(final List<PsiElement> elements, final Collection<LineMarkerInfo> result) {
    super.collectSlowLineMarkers(elements, result);
  }
}

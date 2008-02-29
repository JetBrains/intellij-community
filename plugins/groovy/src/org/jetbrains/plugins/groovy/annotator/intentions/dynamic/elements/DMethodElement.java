package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.MyPair;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.virtual.DynamicVirtualMethod;

import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 12.02.2008
 */
public class DMethodElement extends DItemElement {
  DParameterElement[] myParametersElements;

  public DMethodElement(DynamicVirtualMethod virtualMethod) {
    super(virtualMethod);
  }

  public DMethodElement(DynamicVirtualMethod virtualMethod, boolean isSetParameters) {
    this(virtualMethod);

    if (isSetParameters) {
      final List<MyPair<String, PsiType>> list = virtualMethod.getArguments();
      for (int i = 0; i < list.size(); i++) {
        MyPair<String, PsiType> MyPair = list.get(i);
        addContent(new DParameterElement(MyPair.getFirst(), MyPair.getSecond().getCanonicalText(),i));
      }
    }
  }

  public DParameterElement[] getParametersElements() {
    return myParametersElements;
  }

  public void setParametersElements(DParameterElement[] parametersElements) {
    myParametersElements = parametersElements;
  }

  @NotNull
  public DynamicVirtualMethod getDynamicVirtualElement() {
    return ((DynamicVirtualMethod) super.getDynamicVirtualElement());
  }
}
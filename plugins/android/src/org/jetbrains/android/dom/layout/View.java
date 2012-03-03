package org.jetbrains.android.dom.layout;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.dom.converters.ViewClassConverter;

/**
 * @author Eugene.Kudelevsky
 */
public interface View extends LayoutViewElement {
  @Attribute("class")
  @Convert(ViewClassConverter.class)
  GenericAttributeValue<PsiClass> getViewClass();
}

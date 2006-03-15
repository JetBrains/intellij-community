package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.impl.AntProjectImpl;
import com.intellij.lang.ant.psi.impl.reference.providers.AntDefaultTargetReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;

public class AntReferenceProvidersRegistry {

  private static final GenericReferenceProvider[] EMPTY_ARRAY = new GenericReferenceProvider[0];
  private static AntDefaultTargetReferenceProvider ourDefaultTargetProvider = null;

  private AntReferenceProvidersRegistry() {
  }

  public static GenericReferenceProvider[] getProvidersByElement(final AntElement element) {
    if (element instanceof AntProjectImpl) {
      final AntProjectImpl project = (AntProjectImpl)element;
      final XmlAttribute attribute = project.getSourceElement().getAttribute("default", null);
      if (attribute != null) {
        return new GenericReferenceProvider[]{getDefaultTargetReferenceProvider()};
      }
    }
    return EMPTY_ARRAY;
  }

  private static AntDefaultTargetReferenceProvider getDefaultTargetReferenceProvider() {
    if (ourDefaultTargetProvider == null) {
      ourDefaultTargetProvider = new AntDefaultTargetReferenceProvider();
    }
    return ourDefaultTargetProvider;
  }

}

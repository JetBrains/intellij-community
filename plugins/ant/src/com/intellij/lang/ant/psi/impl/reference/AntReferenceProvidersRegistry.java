package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.impl.*;
import com.intellij.lang.ant.psi.impl.reference.providers.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.util.containers.HashMap;

import java.util.Map;

public class AntReferenceProvidersRegistry {

  private static final GenericReferenceProvider[] EMPTY_ARRAY = new GenericReferenceProvider[0];
  private static final Map<Class, GenericReferenceProvider[]> ourProviders;

  static {
    ourProviders = new HashMap<Class, GenericReferenceProvider[]>();

    final AntElementNameReferenceProvider nameProvider = new AntElementNameReferenceProvider();
    final AntPropertyReferenceProvider propProvider = new AntPropertyReferenceProvider();
    final AntFileReferenceProvider fileProvider = new AntFileReferenceProvider();
    final AntRefIdReferenceProvider refIdProvider = new AntRefIdReferenceProvider();

    ourProviders.put(AntProjectImpl.class,
                     new GenericReferenceProvider[]{new AntSingleTargetReferenceProvider(), nameProvider});
    ourProviders.put(AntTargetImpl.class, new GenericReferenceProvider[]{new AntTargetListReferenceProvider(),
      propProvider, refIdProvider, nameProvider});
    ourProviders.put(AntStructuredElementImpl.class,
                     new GenericReferenceProvider[]{fileProvider, propProvider, refIdProvider, nameProvider});
    ourProviders.put(AntTaskImpl.class, ourProviders.get(AntStructuredElementImpl.class));
    ourProviders.put(AntPropertyImpl.class, ourProviders.get(AntStructuredElementImpl.class));
    ourProviders.put(AntCallImpl.class, new GenericReferenceProvider[]{new AntSingleTargetReferenceProvider(),
      propProvider, refIdProvider, nameProvider});

  }

  private AntReferenceProvidersRegistry() {
  }

  public static GenericReferenceProvider[] getProvidersByElement(final AntElement element) {
    GenericReferenceProvider[] result = ourProviders.get(element.getClass());
    return (result != null) ? result : EMPTY_ARRAY;
  }
}

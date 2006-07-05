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

    final AntAttributeReferenceProvider attrProvider = new AntAttributeReferenceProvider();
    final AntElementNameReferenceProvider nameProvider = new AntElementNameReferenceProvider();
    final AntPropertyReferenceProvider propProvider = new AntPropertyReferenceProvider();
    final AntFileReferenceProvider fileProvider = new AntFileReferenceProvider();
    final AntRefIdReferenceProvider refIdProvider = new AntRefIdReferenceProvider();
    final AntMacroDefParameterReferenceProvider macroParamsProvider = new AntMacroDefParameterReferenceProvider();
    final AntSingleTargetReferenceProvider targetProvider = new AntSingleTargetReferenceProvider();

    ourProviders.put(AntProjectImpl.class, new GenericReferenceProvider[]{targetProvider, nameProvider, attrProvider});
    ourProviders.put(AntTargetImpl.class, new GenericReferenceProvider[]{new AntTargetListReferenceProvider(), propProvider, refIdProvider,
      nameProvider, attrProvider});
    ourProviders.put(AntStructuredElementImpl.class, new GenericReferenceProvider[]{fileProvider, propProvider, refIdProvider, nameProvider,
      attrProvider, macroParamsProvider});
    ourProviders.put(AntTaskImpl.class, ourProviders.get(AntStructuredElementImpl.class));
    ourProviders.put(AntPropertyImpl.class, ourProviders.get(AntStructuredElementImpl.class));
    ourProviders.put(AntMacroDefImpl.class, ourProviders.get(AntStructuredElementImpl.class));
    ourProviders.put(AntPresetDefImpl.class, ourProviders.get(AntStructuredElementImpl.class));
    ourProviders.put(AntTypeDefImpl.class, ourProviders.get(AntStructuredElementImpl.class));
    ourProviders.put(AntImportImpl.class, new GenericReferenceProvider[]{fileProvider, propProvider, nameProvider, attrProvider});
    ourProviders.put(AntAntImpl.class, new GenericReferenceProvider[]{fileProvider, propProvider, nameProvider, attrProvider, targetProvider});
    ourProviders.put(AntCallImpl.class, new GenericReferenceProvider[]{targetProvider, propProvider, refIdProvider, nameProvider,
      attrProvider, macroParamsProvider});

  }

  private AntReferenceProvidersRegistry() {
  }

  public static GenericReferenceProvider[] getProvidersByElement(final AntElement element) {
    GenericReferenceProvider[] result = ourProviders.get(element.getClass());
    return (result != null) ? result : EMPTY_ARRAY;
  }
}

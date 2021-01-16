// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl;

import com.intellij.execution.CantRunException;
import com.intellij.lang.ant.AntBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.Externalizer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Objects;

public abstract class AntReference {
  private static final Logger LOG = Logger.getInstance(AntReference.class);
  @NonNls private static final String PROJECT_DEFAULT_ATTR = "projectDefault";
  @NonNls private static final String NAME_ATTR = "name";
  @NonNls private static final String BUNDLED_ANT_ATTR = "bundledAnt";

  public static final Externalizer<AntReference> EXTERNALIZER = new Externalizer<>() {
    @Override
    public AntReference readValue(Element dataElement) {
      if (Boolean.valueOf(dataElement.getAttributeValue(PROJECT_DEFAULT_ATTR)).booleanValue()) return PROJECT_DEFAULT;
      if (Boolean.valueOf(dataElement.getAttributeValue(BUNDLED_ANT_ATTR)).booleanValue()) return BUNDLED_ANT;
      String name = dataElement.getAttributeValue(NAME_ATTR);
      if (name == null) {
        throw new IllegalStateException("no name");
      }
      return new MissingAntReference(name);
    }

    @Override
    public void writeValue(Element dataElement, AntReference antReference) {
      antReference.writeExternal(dataElement);
    }
  };
  public static final Comparator<AntReference> COMPARATOR = new Comparator<>() {
    @Override
    public int compare(AntReference reference, AntReference reference1) {
      if (reference.equals(reference1)) return 0;
      if (reference == BUNDLED_ANT) return -1;
      if (reference1 == BUNDLED_ANT) return 1;
      return reference.getName().compareToIgnoreCase(reference1.getName());
    }
  };

  protected abstract void writeExternal(Element dataElement);

  public String toString() {
    return getName();
  }

  public static final AntReference PROJECT_DEFAULT = new AntReference() {
    @Override
    protected void writeExternal(Element dataElement) {
      dataElement.setAttribute(PROJECT_DEFAULT_ATTR, Boolean.TRUE.toString());
    }

    @Override
    public AntInstallation find(GlobalAntConfiguration ants) {
      throw new UnsupportedOperationException("Should not call");
    }

    @Override
    public AntReference bind(GlobalAntConfiguration antConfiguration) {
      return this;
    }

    @Override
    public String getName() {
      throw new UnsupportedOperationException("Should not call");
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "PROJECT_DEFAULT";
    }

    public boolean equals(Object obj) {
      return obj == this;
    }
  };

  public static final AntReference BUNDLED_ANT = new AntReference() {
    @Override
    protected void writeExternal(Element dataElement) {
      dataElement.setAttribute(BUNDLED_ANT_ATTR, Boolean.TRUE.toString());
    }

    public boolean equals(Object obj) {
      return obj == this;
    }

    @Override
    public String getName() {
      return GlobalAntConfiguration.getBundledAntName();
    }

    @Override
    public AntInstallation find(GlobalAntConfiguration antConfiguration) {
      return antConfiguration.getBundledAnt();
    }

    @Override
    public AntReference bind(GlobalAntConfiguration antConfiguration) {
      return this;
    }
  };

  public abstract @NlsSafe String getName();

  public abstract AntInstallation find(GlobalAntConfiguration antConfiguration);

  public abstract AntReference bind(GlobalAntConfiguration antConfiguration);

  public int hashCode() {
    return getName().hashCode();
  }

  public boolean equals(Object obj) {
    if (obj == PROJECT_DEFAULT) return this == PROJECT_DEFAULT;
    if (obj == BUNDLED_ANT) return this == BUNDLED_ANT;
    return obj instanceof AntReference && Objects.equals(getName(), ((AntReference)obj).getName());
  }

  @Nullable
  public static AntInstallation findAnt(AbstractProperty<? extends AntReference> property, AbstractProperty.AbstractPropertyContainer container) {
    GlobalAntConfiguration antConfiguration = GlobalAntConfiguration.INSTANCE.get(container);
    LOG.assertTrue(antConfiguration != null);
    AntReference antReference = property.get(container);
    if (antReference == PROJECT_DEFAULT) {
      antReference = AntConfigurationImpl.DEFAULT_ANT.get(container);
    }
    if (antReference == null) return null;
    return antReference.find(antConfiguration);
  }

  public static AntInstallation findNotNullAnt(AbstractProperty<? extends AntReference> property,
                                               AbstractProperty.AbstractPropertyContainer container,
                                               GlobalAntConfiguration antConfiguration) throws CantRunException {
    AntReference antReference = property.get(container);
    if (antReference == PROJECT_DEFAULT) antReference = AntConfigurationImpl.DEFAULT_ANT.get(container);
    if (antReference == null) throw new CantRunException(AntBundle.message("cant.run.ant.no.ant.configured.error.message"));
    AntInstallation antInstallation = antReference.find(antConfiguration);
    if (antInstallation == null) {
      throw new CantRunException(AntBundle.message("cant.run.ant.ant.reference.is.not.configured.error.message", antReference.getName()));
    }
    return antInstallation;
  }

  @Nullable
  public static AntInstallation findAntOrBundled(AbstractProperty.AbstractPropertyContainer container) {
    GlobalAntConfiguration antConfiguration = GlobalAntConfiguration.INSTANCE.get(container);
    if (container.hasProperty(AntBuildFileImpl.ANT_REFERENCE)) return findAnt(AntBuildFileImpl.ANT_REFERENCE, container);
    return antConfiguration.getBundledAnt();
  }

  static class MissingAntReference extends AntReference {
    private final String myName;

    MissingAntReference(String name) {
      myName = name;
    }

    @Override
    protected void writeExternal(Element dataElement) {
      dataElement.setAttribute(NAME_ATTR, myName);
    }

    @Override
    public String getName() {
      return myName;
    }

    @Override
    public AntInstallation find(GlobalAntConfiguration antConfiguration) {
      return antConfiguration.getConfiguredAnts().get(this);
    }

    @Override
    public AntReference bind(GlobalAntConfiguration antConfiguration) {
      AntInstallation antInstallation = find(antConfiguration);
      if (antInstallation != null) return new BindedReference(antInstallation);
      return this;
    }
  }

  static class BindedReference extends AntReference {
    private final AntInstallation myAnt;

    BindedReference(AntInstallation ant) {
      myAnt = ant;
    }

    @Override
    public AntInstallation find(GlobalAntConfiguration antConfiguration) {
      return myAnt;
    }

    @Override
    public String getName() {
      return myAnt.getName();
    }

    @Override
    protected void writeExternal(Element dataElement) {
      dataElement.setAttribute(NAME_ATTR, getName());
    }

    @Override
    public AntReference bind(GlobalAntConfiguration antConfiguration) {
      return this;
    }
  }
}

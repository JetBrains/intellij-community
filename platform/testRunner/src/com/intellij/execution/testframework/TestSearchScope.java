// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework;

import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author dyoma
 */
public interface TestSearchScope {
  @Nullable
  SourceScope getSourceScope(ModuleBasedConfiguration configuration);

  TestSearchScope WHOLE_PROJECT = new TestSearchScope() {
    public SourceScope getSourceScope(final ModuleBasedConfiguration configuration) {
      return SourceScope.wholeProject(configuration.getProject());
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "WHOLE_PROJECT";
    }
  };

  TestSearchScope SINGLE_MODULE = new TestSearchScope() {
    public SourceScope getSourceScope(final ModuleBasedConfiguration configuration) {
      return SourceScope.modules(configuration.getModules());
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "SINGLE_MODULE";
    }
  };

  TestSearchScope MODULE_WITH_DEPENDENCIES = new TestSearchScope() {
    public SourceScope getSourceScope(final ModuleBasedConfiguration configuration) {
      return SourceScope.modulesWithDependencies(configuration.getModules());
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "MODULE_WITH_DEPENDENCIES";
    }
  };

  class Wrapper implements JDOMExternalizable {
    @NonNls private static final String DEFAULT_NAME = "defaultName";
    private TestSearchScope myScope = SINGLE_MODULE;
    @NonNls private static final String WHOLE_PROJECT_NAE = "wholeProject";
    @NonNls private static final String SINGLE_MODULE_NAME = "singleModule";
    @NonNls private static final String MODULE_WITH_DEPENDENCIES_NAME = "moduleWithDependencies";

    public void readExternal(final Element element) throws InvalidDataException {
      final String value = element.getAttributeValue(DEFAULT_NAME);
      if (SINGLE_MODULE_NAME.equals(value)) myScope = SINGLE_MODULE;
      else if (MODULE_WITH_DEPENDENCIES_NAME.equals(value)) myScope = MODULE_WITH_DEPENDENCIES;
      else myScope = WHOLE_PROJECT;
    }

    public void writeExternal(final Element element) throws WriteExternalException {
      String name = WHOLE_PROJECT_NAE;
      if (myScope == SINGLE_MODULE) name = SINGLE_MODULE_NAME;
      else if (myScope == MODULE_WITH_DEPENDENCIES) name = MODULE_WITH_DEPENDENCIES_NAME;
      element.setAttribute(DEFAULT_NAME, name);
    }

    public TestSearchScope getScope() {
      return myScope;
    }

    public void setScope(final TestSearchScope scope) {
      myScope = scope;
    }

    @Override
    public String toString() {
      return myScope == null? "null" : myScope.toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Wrapper)) return false;
      Wrapper wrapper = (Wrapper)o;
      return Objects.equals(myScope, wrapper.myScope);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myScope);
    }
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.ast;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public abstract class Members {

  public abstract @NotNull Collection<PsiMethod> getMethods();

  public abstract @NotNull Collection<GrField> getFields();

  public abstract @NotNull Collection<PsiClass> getClasses();

  public abstract @NotNull Collection<PsiClassType> getImplementsTypes();

  public abstract void addFrom(@NotNull Members other);

  public static final Members EMPTY = new Members() {

    @Override
    public @NotNull Collection<PsiMethod> getMethods() {
      return Collections.emptyList();
    }

    @Override
    public @NotNull Collection<GrField> getFields() {
      return Collections.emptyList();
    }

    @Override
    public @NotNull Collection<PsiClass> getClasses() {
      return Collections.emptyList();
    }

    @Override
    public @NotNull Collection<PsiClassType> getImplementsTypes() {
      return Collections.emptyList();
    }

    @Override
    public void addFrom(@NotNull Members other) {
      // do nothing
    }
  };

  public static @NotNull Members create() {
    return new Members() {

      private final Collection<PsiMethod> methods = new ArrayList<>();
      private final Collection<GrField> fields = new ArrayList<>();
      private final Collection<PsiClass> classes = new ArrayList<>();
      private final Collection<PsiClassType> implementsTypes = new ArrayList<>();

      @Override
      public @NotNull Collection<PsiMethod> getMethods() {
        return methods;
      }

      @Override
      public @NotNull Collection<GrField> getFields() {
        return fields;
      }

      @Override
      public @NotNull Collection<PsiClass> getClasses() {
        return classes;
      }

      @Override
      public @NotNull Collection<PsiClassType> getImplementsTypes() {
        return implementsTypes;
      }

      @Override
      public void addFrom(@NotNull Members other) {
        methods.addAll(other.getMethods());
        fields.addAll(other.getFields());
        classes.addAll(other.getClasses());
        implementsTypes.addAll(other.getImplementsTypes());
      }
    };
  }
}

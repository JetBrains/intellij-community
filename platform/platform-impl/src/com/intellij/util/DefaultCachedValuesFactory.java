package com.intellij.util;

import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class DefaultCachedValuesFactory implements CachedValuesFactory {
  private final Project myProject;

  public DefaultCachedValuesFactory(Project project) {
    myProject = project;
  }

  public <T> CachedValue<T> createCachedValue(@NotNull CachedValueProvider<T> provider, boolean trackValue) {
    return trackValue ? new CachedValueImpl<T>(provider) {
      @Override
      protected Object[] getDependencies(CachedValueProvider.Result<T> result) {
        return getDependenciesPlusValue(result);
      }

      @Override
      public boolean isFromMyProject(Project project) {
        return myProject == project;
      }
    } : new CachedValueImpl<T>(provider) {

      @Override
      public boolean isFromMyProject(Project project) {
        return myProject == project;
      }
    };
  }

  public <T, P> ParameterizedCachedValue<T, P> createParameterizedCachedValue(@NotNull ParameterizedCachedValueProvider<T, P> provider,
                                                                              boolean trackValue) {
    return trackValue ? new ParameterizedCachedValueImpl<T, P>(provider) {
      @Override
      public boolean isFromMyProject(Project project) {
        return myProject == project;
      }

      @Override
      protected Object[] getDependencies(CachedValueProvider.Result<T> tResult) {
        return getDependenciesPlusValue(tResult);
      }
    } : new ParameterizedCachedValueImpl<T, P>(provider) {
      @Override
      public boolean isFromMyProject(Project project) {
        return myProject == project;
      }
    };
  }
}

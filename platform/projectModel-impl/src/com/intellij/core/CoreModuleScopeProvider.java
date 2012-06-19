/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.core;

import com.intellij.openapi.module.impl.ModuleScopeProvider;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * Author: dmitrylomov
 */
public class CoreModuleScopeProvider implements ModuleScopeProvider {
  public CoreModuleScopeProvider() {
  }

  @Override
  public GlobalSearchScope getModuleScope() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleScope(boolean includeTests) {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleWithLibrariesScope() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleWithDependenciesScope() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleContentScope() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleContentWithDependenciesScope() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleWithDependenciesAndLibrariesScope(boolean includeTests) {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleWithDependentsScope() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleTestsWithDependentsScope() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getModuleRuntimeScope(boolean includeTests) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearCache() {
    throw new UnsupportedOperationException();
  }
}

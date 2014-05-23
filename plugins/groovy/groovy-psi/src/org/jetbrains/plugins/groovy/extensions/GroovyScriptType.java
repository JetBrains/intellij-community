/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author ilyas
 */
public abstract class GroovyScriptType {

  private final String id;

  protected GroovyScriptType(String id) {
    this.id = id;
  }

  @NotNull
  public String getId() {
    return id;
  }

  @NotNull
  public abstract Icon getScriptIcon();

  public GlobalSearchScope patchResolveScope(@NotNull GroovyFile file, @NotNull GlobalSearchScope baseScope) {
    return baseScope;
  }

  public List<String> appendImplicitImports(@NotNull GroovyFile file) {
    return Collections.emptyList();
  }

  public boolean shouldBeCompiled(GroovyFile script) {
    return false;
  }

}

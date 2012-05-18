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
package com.intellij.openapi.application;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jdom.Attribute;
import org.jdom.Text;

/**
 * Allows to disable expansion of path macros in the values of certain properties.
 *
 * @author yole
 */
public abstract class PathMacroFilter {
  public static final ExtensionPointName<PathMacroFilter> EP_NAME = ExtensionPointName.create("com.intellij.pathMacroFilter");

  public boolean skipPathMacros(Text element) {
    return false;
  }

  public boolean skipPathMacros(Attribute attribute) {
    return false;
  }

  public boolean recursePathMacros(Text element) {
    return false;
  }

  public boolean recursePathMacros(Attribute attribute) {
    return false;
  }
}

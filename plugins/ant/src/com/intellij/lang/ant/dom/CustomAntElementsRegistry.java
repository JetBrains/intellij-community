/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.util.xml.XmlName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Storage for user-defined tasks and data types
 * parsed from ant files
 * @author Eugene Zhuravlev
 *         Date: Jul 1, 2010
 */
public class CustomAntElementsRegistry {

  private CustomAntElementsRegistry() {
  }

  public static CustomAntElementsRegistry getInstance(AntDomProject antProject) {
    // todo: walk the project and all its includes, gather taskdefs and load tasks
    return new CustomAntElementsRegistry(); // todo: caching
  }

  @NotNull
  public Set<XmlName> getCompletionVariants(AntDomElement parentElement) {
    final HashSet<XmlName> set = new HashSet<XmlName>();
    set.add(new XmlName("some_custom_tag"));
    return set;
  }

  @Nullable
  public AntDomElement findDeclaringElement(AntDomElement parentElement, XmlName customElementName) {
    return null;
  }

  private static class CustomTagFinder extends AntDomRecursiveVisitor {
    
  }
}

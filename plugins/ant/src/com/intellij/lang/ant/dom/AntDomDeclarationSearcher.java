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

import com.intellij.util.xml.AbstractDomDeclarationSearcher;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.GenericDomValue;

/**
 * @author Eugene Zhuravlev
 */
public class AntDomDeclarationSearcher extends AbstractDomDeclarationSearcher {

  protected DomTarget createDomTarget(DomElement parent, DomElement nameElement) {
    if (parent instanceof AntDomElement && nameElement.equals(((AntDomElement)parent).getId())) { // id attrib is defined
      return DomTarget.getTarget(parent, (GenericDomValue)nameElement);
    }
    if (parent instanceof AntDomProperty && nameElement.equals(((AntDomProperty)parent).getEnvironment())) { // environment attrib is defined
      return DomTarget.getTarget(parent, (GenericDomValue)nameElement);
    }
    return null;
  }
}

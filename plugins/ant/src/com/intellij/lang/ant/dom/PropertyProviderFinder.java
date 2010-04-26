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

import com.intellij.util.xml.DomElement;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 22, 2010
 */
public abstract class PropertyProviderFinder extends AntDomRecursiveVisitor {
  // todo: when searching for properties walk elements in the target dependency-based order

  private final AntDomElement myContextElement;
  private boolean myStopped;

  private Set<AntDomTarget> myVisitedTargets = new HashSet<AntDomTarget>();

  public PropertyProviderFinder(DomElement contextElement) {
    myContextElement = contextElement.getParentOfType(AntDomElement.class, false);
  }

  @Override
  public void visitAntDomElement(AntDomElement element) {
    if (myStopped) {
      return; 
    }
    if (element.equals(myContextElement)) {
      stop();
    }
    else {
      if (element instanceof PropertiesProvider) {
        propertyProviderFound(((PropertiesProvider)element));
      }
    }
    if (!myStopped) {
      super.visitAntDomElement(element);
    }
  }

  public AntDomElement getContextElement() {
    return myContextElement;
  }

  protected void stop() {
    myStopped = true;
  }

  /**
   * @param propertiesProvider
   * @return true if search should be continued and false in order to stop
   */
  protected abstract void propertyProviderFound(PropertiesProvider propertiesProvider);

  //@Override
  //public void visitTarget(AntDomTarget target) {
  //  if (!myVisitedTargets.contains(target)) {
  //    final List<XmlAttributeValue> dependentTargets = target.getDependsList().getValue();
  //    for (XmlAttributeValue attributeValue : dependentTargets) {
  //      AntSupport.getAntDomElement()
  //    }
  //    super.visitTarget(target);
  //  }
  //}
}

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
import com.intellij.util.xml.DomElementVisitor;

import java.util.Iterator;

/**
 * @author Eugene Zhuravlev
 */
public class AntDomRecursiveVisitor implements DomElementVisitor{
  public void visitDomElement(DomElement element) {
  }

  public void visitAntDomElement(AntDomElement element) {
    for (Iterator<AntDomElement> iterator = element.getAntChildrenIterator(); iterator.hasNext();) {
      AntDomElement child = iterator.next();
      child.accept(this);
    }
  }

  public void visitAntDomCustomElement(AntDomCustomElement custom) {
    visitAntDomElement(custom);
  }
  

  public void visitTypeDef(AntDomTypeDef typedef) {
    visitAntDomElement(typedef);
  }

  public void visitTaskDef(AntDomTaskdef typedef) {
    visitTypeDef(typedef);
  }

  public void visitMacroDef(AntDomMacroDef macrodef) {
    visitAntDomElement(macrodef);
  }

  public void visitPresetDef(AntDomPresetDef presetdef) {
    visitAntDomElement(presetdef);
  }

  public void visitScriptDef(AntDomScriptDef scriptdef) {
    visitAntDomElement(scriptdef);
  }

  public void visitTarget(AntDomTarget target) {
    visitAntDomElement(target);
  }

  public void visitProject(AntDomProject project) {
    visitAntDomElement(project);
  }

  public void visitProperty(AntDomProperty property) {
    visitAntDomElement(property);
  }

  public void visitInclude(AntDomInclude includeTag) {
    visitAntDomElement(includeTag);
  }

  public void visitImport(AntDomImport importTag) {
    visitAntDomElement(importTag);
  }
  
  public void visitAntDomAntCall(AntDomAntCall antCall) {
    visitAntDomElement(antCall);
  }
  
  public void visitAntDomAntCallParam(AntDomAntCallParam antCallParam) {
    visitAntDomElement(antCallParam);
  }
  
}

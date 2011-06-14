/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.todo;

import com.intellij.openapi.util.JDOMExternalizable;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Iterator;

/**
 * @author Vladimir Kondratyev
 */
public class TodoPanelSettings implements JDOMExternalizable {
  public boolean myArePackagesShown;
  public boolean myAreModulesShown;
  public boolean myAreFlattenPackages;
  public boolean myIsAutoScrollToSource;
  public String myTodoFilterName;
  public boolean myShowPreview;

  @NonNls private static final String ATTRIBUTE_VALUE = "value";
  @NonNls private static final String ELEMENT_ARE_PACKAGES_SHOWN = "are-packages-shown";
  @NonNls private static final String ELEMENT_ARE_MODULES_SHOWN = "are-modules-shown";
  @NonNls private static final String ELEMENT_FLATTEN_PACKAGES = "flatten-packages";
  @NonNls private static final String ELEMENT_AUTOSCROLL_TO_SOURCE = "is-autoscroll-to-source";
  @NonNls private static final String ELEMENT_TODO_FILTER = "todo-filter";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls private static final String ELEMENT_IS_PREVIEW_ENABLED = "is-preview-enabled";

  public TodoPanelSettings() {
  }

  public TodoPanelSettings(TodoPanelSettings s) {
    myArePackagesShown = s.myArePackagesShown;
    myAreModulesShown = s.myAreModulesShown;
    myAreFlattenPackages = s.myAreFlattenPackages;
    myIsAutoScrollToSource = s.myIsAutoScrollToSource;
    myTodoFilterName = s.myTodoFilterName;
  }

  public void readExternal(Element e){
    for(Iterator i=e.getChildren().iterator();i.hasNext();){
      Element child=(Element)i.next();
      final String childName = child.getName();
      if(ELEMENT_ARE_PACKAGES_SHOWN.equals(childName)){
        myArePackagesShown=Boolean.valueOf(child.getAttributeValue(ATTRIBUTE_VALUE)).booleanValue();
      }
      else if(ELEMENT_ARE_MODULES_SHOWN.equals(childName)){
        myAreModulesShown=Boolean.valueOf(child.getAttributeValue(ATTRIBUTE_VALUE)).booleanValue();
      }
      else if(ELEMENT_FLATTEN_PACKAGES.equals(childName)){
        myAreFlattenPackages=Boolean.valueOf(child.getAttributeValue(ATTRIBUTE_VALUE)).booleanValue();
      }
      else if(ELEMENT_AUTOSCROLL_TO_SOURCE.equals(childName)){
        myIsAutoScrollToSource=Boolean.valueOf(child.getAttributeValue(ATTRIBUTE_VALUE)).booleanValue();
      }
      else if(ELEMENT_TODO_FILTER.equals(childName)){
        myTodoFilterName=child.getAttributeValue(ATTRIBUTE_NAME);
      } else if (ELEMENT_IS_PREVIEW_ENABLED.equals(childName)) {
        myShowPreview = Boolean.valueOf(child.getAttributeValue(ELEMENT_IS_PREVIEW_ENABLED)).booleanValue();
      }
    }
  }

  public void writeExternal(Element e){
    Element areArePackagesShownElement=new Element(ELEMENT_ARE_PACKAGES_SHOWN);
    areArePackagesShownElement.setAttribute(ATTRIBUTE_VALUE,myArePackagesShown?Boolean.TRUE.toString():Boolean.FALSE.toString());
    e.addContent(areArePackagesShownElement);

    Element areModulesShownElement=new Element(ELEMENT_ARE_MODULES_SHOWN);
    areModulesShownElement.setAttribute(ATTRIBUTE_VALUE,myAreModulesShown?Boolean.TRUE.toString():Boolean.FALSE.toString());
    e.addContent(areModulesShownElement);

    Element areAreFlattenPackagesElement=new Element(ELEMENT_FLATTEN_PACKAGES);
    areAreFlattenPackagesElement.setAttribute(ATTRIBUTE_VALUE,myAreFlattenPackages?Boolean.TRUE.toString():Boolean.FALSE.toString());
    e.addContent(areAreFlattenPackagesElement);

    Element isAutoScrollModeElement=new Element(ELEMENT_AUTOSCROLL_TO_SOURCE);
    isAutoScrollModeElement.setAttribute(ATTRIBUTE_VALUE,myIsAutoScrollToSource?Boolean.TRUE.toString():Boolean.FALSE.toString());
    e.addContent(isAutoScrollModeElement);

    if(myTodoFilterName!=null){
      Element todoFilterElement=new Element(ELEMENT_TODO_FILTER);
      todoFilterElement.setAttribute(ATTRIBUTE_NAME,myTodoFilterName);
      e.addContent(todoFilterElement);
    }

    if (myShowPreview) {
      Element showPreviewElement = new Element(ELEMENT_IS_PREVIEW_ENABLED);
      showPreviewElement.setAttribute(ATTRIBUTE_VALUE, Boolean.TRUE.toString());
      e.addContent(showPreviewElement);
    }
  }

  public boolean areModulesShown() {
    return myAreModulesShown;
  }

  public void setShownModules(boolean state) {
    myAreModulesShown = state;
  }

  public boolean arePackagesShown(){
    return myArePackagesShown;
  }

  public void setShownPackages(boolean state){
    myArePackagesShown=state;
  }

  public boolean areFlattenPackages(){
    return myAreFlattenPackages;
  }

  public void setAreFlattenPackages(boolean state){
    myAreFlattenPackages=state;
  }

  public boolean isAutoScrollToSource(){
    return myIsAutoScrollToSource;
  }

  public void setAutoScrollToSource(boolean state){
    myIsAutoScrollToSource=state;
  }

  public String getTodoFilterName(){
    return myTodoFilterName;
  }

  public void setTodoFilterName(String todoFilterName){
    myTodoFilterName=todoFilterName;
  }

  public boolean isShowPreview() {
    return myShowPreview;
  }

  public void setShowPreview(boolean showPreview) {
    myShowPreview = showPreview;
  }
}

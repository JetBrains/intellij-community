// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.mock;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class MockFacetConfiguration implements FacetConfiguration {
  private final List<String> myRootUrls = new ArrayList<>();
  private String myData = "";
  private MockFacetEditorTab myEditor;

  public MockFacetConfiguration(String data) {
    myData = data;
  }

  public MockFacetConfiguration() {
  }

  @Override
  public FacetEditorTab[] createEditorTabs(final FacetEditorContext editorContext, final FacetValidatorsManager validatorsManager) {
    myEditor = new MockFacetEditorTab(this);
    return new FacetEditorTab[]{myEditor};
  }

  public MockFacetEditorTab getEditor() {
    return myEditor;
  }

  public void addRoot(VirtualFile root) {
    myRootUrls.add(root.getUrl());
  }

  public void addRoot(String url) {
    myRootUrls.add(url);
  }

  public void removeRoot(VirtualFile root) {
    myRootUrls.remove(root.getUrl());
  }

  public void setData(final String data) {
    myData = data;
  }

  public String getData() {
    return myData;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    myData = StringUtil.notNullize(element.getAttributeValue("data"));
    myRootUrls.clear();
    final List<Element> children = element.getChildren("root");
    for (Element child : children) {
      myRootUrls.add(child.getAttributeValue("url"));
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    if (!myData.isEmpty()) {
      element.setAttribute("data", myData);
    }
    for (String url : myRootUrls) {
      element.addContent(new Element("root").setAttribute("url", url));
    }
  }

  public Collection<String> getRootUrls() {
    return myRootUrls;
  }
}

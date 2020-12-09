// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet.mock;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MockFacetConfiguration implements FacetConfiguration {
  private final List<VirtualFile> myRoots = new ArrayList<>();
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
    myRoots.add(root);
  }

  public void removeRoot(VirtualFile root) {
    myRoots.remove(root);
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
    myRoots.clear();
    final List<Element> children = element.getChildren("root");
    for (Element child : children) {
      myRoots.add(VirtualFileManager.getInstance().findFileByUrl(child.getAttributeValue("url")));
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    if (!myData.isEmpty()) {
      element.setAttribute("data", myData);
    }
    for (VirtualFile root : myRoots) {
      element.addContent(new Element("root").setAttribute("url", root.getUrl()));
    }
  }

  public Collection<VirtualFile> getRoots() {
    return myRoots;
  }
}

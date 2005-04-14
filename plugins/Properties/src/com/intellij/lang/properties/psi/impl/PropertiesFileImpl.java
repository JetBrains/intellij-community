package com.intellij.lang.properties.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.PropertiesElementTypes;
import com.intellij.lang.properties.PropertiesSupportLoader;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2005
 * Time: 12:25:04 AM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesFileImpl extends PsiFileBase implements PropertiesFile {
  private Map<String,Property> myProperties;

  public PropertiesFileImpl(Project project, VirtualFile file) {
    super(project, file, PropertiesSupportLoader.FILE_TYPE.getLanguage());
  }

  public PropertiesFileImpl(Project project, String name, CharSequence text) {
    super(project, name, text, PropertiesSupportLoader.FILE_TYPE.getLanguage());
  }

  public FileType getFileType() {
    return PropertiesSupportLoader.FILE_TYPE;
  }

  public String toString() {
    return "Property file:" + getName();
  }

  public Property[] getProperties() {
    if (myProperties == null) {
      readProperties();
    }
    return myProperties.values().toArray(new Property[myProperties.size()]);
  }

  private void readProperties() {
    final ASTNode[] props = getNode().findChildrenByFilter(PropertiesElementTypes.PROPERTIES);
    myProperties = new LinkedHashMap<String, Property>();
    for (int i = 0; i < props.length; i++) {
      final ASTNode prop = props[i];
      final Property property = (Property)prop.getPsi();
      myProperties.put(property.getKey(), property);
    }
  }

  public Property findPropertyByKey(String key) {
    if (myProperties == null) {
      readProperties();
    }
    return myProperties.get(key);
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    myProperties = null;
  }
}
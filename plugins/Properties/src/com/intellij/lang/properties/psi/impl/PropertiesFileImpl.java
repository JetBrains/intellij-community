package com.intellij.lang.properties.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.lang.properties.PropertiesElementTypes;
import com.intellij.lang.properties.PropertiesSupportLoader;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.util.SmartList;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2005
 * Time: 12:25:04 AM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesFileImpl extends PsiFileBase implements PropertiesFile {
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
    final ASTNode[] props = getNode().findChildrenByFilter(PropertiesElementTypes.PROPERTIES);
    List<Property> list = new SmartList<Property>();
    for (int i = 0; i < props.length; i++) {
      final ASTNode prop = props[i];
      final Property property = (Property)prop.getPsi();
      list.add(property);
    }
    return list.toArray(new Property[list.size()]);
  }

  public Property findPropertyByKey(String key) {
    Property[] properties = getProperties();
    for (int i = 0; i < properties.length; i++) {
      Property property = properties[i];
      if (key.equals(property.getKey())) return property;
    }
    return null;
  }
}
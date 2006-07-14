package com.intellij.lang.ant.config.impl;

import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.PathUtil;
import com.intellij.util.config.Externalizer;
import org.jdom.Element;

import java.io.File;
import java.util.List;

public interface AntClasspathEntry {
  Externalizer<AntClasspathEntry> EXTERNALIZER = new Externalizer<AntClasspathEntry>() {
    public AntClasspathEntry readValue(Element dataElement) throws InvalidDataException {
      String pathUrl = dataElement.getAttributeValue(SinglePathEntry.PATH);
      if (pathUrl != null)
        return new SinglePathEntry(PathUtil.toPresentableUrl(pathUrl));
      String dirUrl = dataElement.getAttributeValue(AllJarsUnderDirEntry.DIR);
      if (dirUrl != null)
        return new AllJarsUnderDirEntry(PathUtil.toPresentableUrl(dirUrl));
      throw new InvalidDataException();
    }

    public void writeValue(Element dataElement, AntClasspathEntry entry) throws WriteExternalException {
      entry.writeExternal(dataElement);
    }
  };

  String getPresentablePath();

  void writeExternal(Element dataElement) throws WriteExternalException;

  void addFilesTo(List<File> files);

  CompositeAppearance getAppearance();
}

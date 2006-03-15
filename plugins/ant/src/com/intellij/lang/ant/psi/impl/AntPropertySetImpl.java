package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntPropertySet;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;

public class AntPropertySetImpl extends AntElementImpl implements AntPropertySet {
  private String myName;
  private String myValue;
  private String myFile;
  private String myLocation;
  private String myRefId;

  public AntPropertySetImpl(AntProject parent, final XmlTag tag) {
    super(parent, tag);
  }

  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntPropertySet: [");
      final Properties properties = getProperties();
      final Enumeration e = properties.propertyNames();
      if (e.hasMoreElements()) {
        String key = (String)e.nextElement();
        while (true) {
          builder.append(key);
          builder.append(" = ");
          builder.append(properties.getProperty(key));
          if(!e.hasMoreElements()) {
            break;
          }
          builder.append(", ");
        }
      }
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @Nullable
  public String getName() {
    parseTag();
    return myName;
  }

  @NotNull
  public Properties getProperties() {
    parseTag();
    Properties result = new Properties();
    if (myValue != null) {
      result.setProperty(myName, myValue);
    }
    else if (myLocation != null) {
      result.setProperty(myName, myLocation);
    }
    else if (myRefId != null) {
      result.setProperty(myName, myRefId);
    }
    else if (myFile != null) {
      try {
        FileInputStream fis = null;
        try {
          fis = new FileInputStream(new File(((AntProject)getAntParent()).getBaseDir(), myFile));
          result.load(fis);
        }
        finally {
          if (fis != null) {
            fis.close();
          }
        }
      }
      catch (IOException e) {
        result = new Properties();
      }
    }
    return result;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private void parseTag() {
    if (myName == null) {
      final XmlTag tag = (XmlTag)getSourceElement();
      final String name = tag.getName();
      if ("property".compareToIgnoreCase(name) == 0) {
        myName = tag.getAttributeValue("name");
        myValue = tag.getAttributeValue("value");
        myLocation = tag.getAttributeValue("location");
        myRefId = tag.getAttributeValue("refid");
        myFile = tag.getAttributeValue("file");
      }
    }
  }
}

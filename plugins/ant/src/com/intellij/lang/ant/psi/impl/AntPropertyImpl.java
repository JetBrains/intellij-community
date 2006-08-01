package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.psi.AntCall;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProperty;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class AntPropertyImpl extends AntTaskImpl implements AntProperty {

  private AntElement myPropHolder;
  private PsiElement myPropertiesFile;

  public AntPropertyImpl(final AntElement parent,
                         final XmlElement sourceElement,
                         final AntTypeDefinition definition,
                         @NonNls final String nameElementAttribute) {
    super(parent, sourceElement, definition, nameElementAttribute);
    myPropHolder = parent;
    if (myPropHolder instanceof AntCall) {
      myPropHolder = myPropHolder.getAntProject();
    }
    final String name = getName();
    if (name != null) {
      myPropHolder.setProperty(name, this);
    }
    else {
      final String environment = getEnvironment();
      if (environment != null) {
        getAntProject().addEnvironmentPropertyPrefix(environment);
      }
      else {
        final XmlTag se = getSourceElement();
        if ("tstamp".equals(se.getName())) {
          String prefix = se.getAttributeValue("prefix");
          if (prefix == null) {
            prefix = "";
          }
          else {
            prefix += '.';
          }
          myPropHolder.setProperty(prefix + "DSTAMP", this);
          myPropHolder.setProperty(prefix + "TSTAMP", this);
          myPropHolder.setProperty(prefix + "TODAY", this);
          final XmlAttributeValue value = getTstampPropertyAttributeValue();
          if (value != null && value.getValue() != null) {
            myPropHolder.setProperty(value.getValue(), this);
          }
        }
      }
    }
  }

  public AntPropertyImpl(final AntElement parent, final XmlElement sourceElement, final AntTypeDefinition definition) {
    this(parent, sourceElement, definition, "name");
  }

  public String toString() {
    final @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntProperty[");
      if (getName() != null) {
        builder.append(getName());
        builder.append(" = ");
        builder.append(getValue(null));
      }
      else {
        final String propFile = getFileName();
        if (propFile != null) {
          builder.append("file: ");
          builder.append(propFile);
        }
        else {
          builder.append(getSourceElement().getName());
        }
      }
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public String getName() {
    final XmlAttributeValue value = getTstampPropertyAttributeValue();
    return (value != null) ? value.getValue() : super.getName();
  }

  public AntElementRole getRole() {
    return AntElementRole.PROPERTY_ROLE;
  }

  public String getFileReferenceAttribute() {
    return "file";
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @Nullable
  public String getValue(final String propName) {
    final XmlTag se = getSourceElement();
    final String tagName = se.getName();
    if ("property".equals(tagName) || "param".equals(tagName)) {
      return getPropertyValue();
    }
    else if ("dirname".equals(tagName)) {
      return getDirnameValue();
    }
    else if ("tstamp".equals(tagName)) {
      return getTstampValue(propName);
    }
    return null;
  }

  @Nullable
  public String getFileName() {
    return getSourceElement().getAttributeValue("file");
  }

  @Nullable
  public PropertiesFile getPropertiesFile() {
    if (myPropertiesFile == null) {
      myPropertiesFile = AntElementImpl.ourNull;
      final String name = getFileName();
      if (name != null) {
        final PsiFile psiFile = findFileByName(name);
        if (psiFile instanceof PropertiesFile) {
          myPropertiesFile = psiFile;
        }
      }
    }
    return (myPropertiesFile == AntElementImpl.ourNull) ? null : (PropertiesFile)myPropertiesFile;
  }

  @Nullable
  public String getPrefix() {
    return computeAttributeValue(getSourceElement().getAttributeValue("prefix"));
  }

  @Nullable
  public String getEnvironment() {
    return computeAttributeValue(getSourceElement().getAttributeValue("environment"));
  }

  public void clearCaches() {
    super.clearCaches();
    myPropHolder.clearCaches();
    myPropertiesFile = null;
  }

  public int getTextOffset() {
    final XmlAttributeValue value = getTstampPropertyAttributeValue();
    return (value != null) ? value.getTextOffset() : super.getTextOffset();
  }

  /**
   * @return <format> element for the <tstamp> property
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  public AntElement getFormatElement() {
    if (getTstampPropertyAttributeValue() != null) {
      for (final AntElement child : getChildren()) {
        if (child instanceof AntStructuredElement) {
          final AntStructuredElement se = (AntStructuredElement)child;
          if (se.getSourceElement().getName().equals("format")) {
            return child;
          }
        }
      }
    }
    return this;
  }

  @Nullable
  private String getPropertyValue() {
    final XmlTag sourceElement = getSourceElement();
    String value = sourceElement.getAttributeValue("value");
    if (value != null) {
      return computeAttributeValue(value);
    }
    value = computeAttributeValue(sourceElement.getAttributeValue("location"));
    if (value != null) {
      final String baseDir = getAntProject().getBaseDir();
      if (baseDir != null) {
        return new File(baseDir, value).getAbsolutePath();
      }
    }
    return value;
  }

  @Nullable
  private String getDirnameValue() {
    final XmlTag sourceElement = getSourceElement();
    final String value = computeAttributeValue(sourceElement.getAttributeValue("file"));
    if (value != null) {
      return new File(value).getParent();
    }
    return value;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private String getTstampValue(final String propName) {
    final XmlTag se = getSourceElement();
    Date d = new Date();
    final XmlTag formatTag = se.findFirstSubTag("format");
    if (formatTag != null) {
      final String offsetStr = formatTag.getAttributeValue("offset");
      int offset;
      if (offsetStr != null) {
        try {
          offset = Integer.parseInt(offsetStr);
        }
        catch (NumberFormatException e) {
          offset = 0;
        }
        final String unitStr = formatTag.getAttributeValue("unit");
        int unit = 0;
        if (unitStr != null) {
          if ("millisecond".equals(unitStr)) {
            unit = Calendar.MILLISECOND;
          }
          else if ("second".equals(unitStr)) {
            unit = Calendar.SECOND;
          }
          else if ("minute".equals(unitStr)) {
            unit = Calendar.MINUTE;
          }
          else if ("hour".equals(unitStr)) {
            unit = Calendar.HOUR_OF_DAY;
          }
          else if ("day".equals(unitStr)) {
            unit = Calendar.DAY_OF_MONTH;
          }
          else if ("week".equals(unitStr)) {
            unit = Calendar.WEEK_OF_YEAR;
          }
          else if ("year".equals(unitStr)) {
            unit = Calendar.YEAR;
          }
        }
        if (offset != 0 && unit != 0) {
          final Calendar cal = Calendar.getInstance();
          cal.setTime(d);
          cal.add(unit, offset);
          d = cal.getTime();
        }
      }
    }
    if (propName != null) {
      if (propName.equals("DSTAMP")) {
        return new SimpleDateFormat("yyyyMMdd").format(d);
      }
      else if (propName.equals("TSTAMP")) {
        return new SimpleDateFormat("HHmm").format(d);
      }
      else if (propName.equals("TODAY")) {
        return new SimpleDateFormat("MMMM d yyyy", Locale.US).format(d);
      }
    }
    final XmlAttributeValue value = getTstampPropertyAttributeValue();
    if (value != null && (propName == null || propName.equals(value.getValue()))) {
      if (formatTag != null) {
        final String pattern = formatTag.getAttributeValue("pattern");
        final DateFormat format = (pattern != null) ? new SimpleDateFormat(pattern) : DateFormat.getTimeInstance();
        final String tz = formatTag.getAttributeValue("timezone");
        if (tz != null) {
          format.setTimeZone(TimeZone.getTimeZone(tz));
        }
        return format.format(d);
      }
    }
    return null;
  }

  @Nullable
  private XmlAttributeValue getTstampPropertyAttributeValue() {
    final XmlTag se = getSourceElement();
    if ("tstamp".equals(se.getName())) {
      final XmlTag formatTag = se.findFirstSubTag("format");
      if (formatTag != null) {
        final XmlAttribute propAttr = formatTag.getAttribute("property", null);
        if (propAttr != null) {
          final XmlAttributeValue value = propAttr.getValueElement();
          if (value != null) {
            return value;
          }
        }
      }
    }
    return null;
  }
}

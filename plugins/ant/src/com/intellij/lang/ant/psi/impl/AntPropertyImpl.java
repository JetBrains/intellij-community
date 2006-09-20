package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.StringSetSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class AntPropertyImpl extends AntTaskImpl implements AntProperty {

  @NonNls private static final String TSTAMP_TAG = "tstamp";

  @NonNls private static final Map<String, String> ourFileRefAttributes;

  static {
    ourFileRefAttributes = new HashMap<String, String>();
    ourFileRefAttributes.put(AntFileImpl.PROPERTY, AntFileImpl.FILE_ATTR);
    ourFileRefAttributes.put("loadfile", "srcfile");
  }

  private AntElement myPropHolder;
  private PsiElement myPropertiesFile;

  public AntPropertyImpl(final AntElement parent,
                         final XmlTag sourceElement,
                         final AntTypeDefinition definition,
                         @NonNls final String nameElementAttribute) {
    super(parent, sourceElement, definition, nameElementAttribute);
    final AntProject project = getAntProject();
    myPropHolder = parent;
    if (parent instanceof AntTarget || parent instanceof AntCall) {
      myPropHolder = project;
    }
    final String name = getName();
    if (name != null) {
      myPropHolder.setProperty(name, this);
    }
    else {
      final String environment = getEnvironment();
      if (environment != null) {
        project.addEnvironmentPropertyPrefix(environment);
      }
      else {
        if (isTstamp()) {
          String prefix = getSourceElement().getAttributeValue(AntFileImpl.PREFIX_ATTR);
          if (prefix == null) {
            myPropHolder.setProperty("DSTAMP", this);
            myPropHolder.setProperty("TSTAMP", this);
            myPropHolder.setProperty("TODAY", this);
          }
          else {
            prefix += '.';
            myPropHolder.setProperty(prefix + "DSTAMP", this);
            myPropHolder.setProperty(prefix + "TSTAMP", this);
            myPropHolder.setProperty(prefix + "TODAY", this);
          }
          final XmlAttributeValue value = getTstampPropertyAttributeValue();
          if (value != null && value.getValue() != null) {
            myPropHolder.setProperty(value.getValue(), this);
          }
        }
      }
    }
  }

  public AntPropertyImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    this(parent, sourceElement, definition, AntFileImpl.NAME_ATTR);
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

  public boolean canRename() {
    return super.canRename() && (!isTstamp() || getTstampPropertyAttributeValue() != null);
  }

  public String getFileReferenceAttribute() {
    final String attrName;
    synchronized (ourFileRefAttributes) {
      attrName = ourFileRefAttributes.get(getSourceElement().getName());
    }
    return (attrName != null) ? attrName : super.getFileReferenceAttribute();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @Nullable
  public String getValue(final String propName) {
    final XmlTag se = getSourceElement();
    final String tagName = se.getName();
    if (AntFileImpl.PROPERTY.equals(tagName) || "param".equals(tagName)) {
      return getPropertyValue();
    }
    else if ("dirname".equals(tagName)) {
      return getDirnameValue();
    }
    else if (isTstamp()) {
      return getTstampValue(propName);
    }
    return null;
  }

  @Nullable
  public String getFileName() {
    return getSourceElement().getAttributeValue(AntFileImpl.FILE_ATTR);
  }

  @Nullable
  public PropertiesFile getPropertiesFile() {
    if (myPropertiesFile == null) {
      myPropertiesFile = AntElementImpl.ourNull;
      final String name = getFileName();
      if (name != null) {
        final PsiFile psiFile = findFileByName(name, null);
        if (psiFile instanceof PropertiesFile) {
          myPropertiesFile = psiFile;
        }
      }
    }
    return (myPropertiesFile == AntElementImpl.ourNull) ? null : (PropertiesFile)myPropertiesFile;
  }

  @Nullable
  public String getPrefix() {
    return computeAttributeValue(getSourceElement().getAttributeValue(AntFileImpl.PREFIX_ATTR));
  }

  @Nullable
  public String getEnvironment() {
    return computeAttributeValue(getSourceElement().getAttributeValue("environment"));
  }

  @Nullable
  public String[] getNames() {
    if (isTstamp()) {
      final Set<String> strings = StringSetSpinAllocator.alloc();
      try {
        String prefix = getSourceElement().getAttributeValue(AntFileImpl.PREFIX_ATTR);
        if (prefix == null) {
          strings.add("DSTAMP");
          strings.add("TSTAMP");
          strings.add("TODAY");
        }
        else {
          prefix += '.';
          strings.add(prefix + "DSTAMP");
          strings.add(prefix + "TSTAMP");
          strings.add(prefix + "TODAY");
        }
        final XmlAttributeValue value = getTstampPropertyAttributeValue();
        if (value != null && value.getValue() != null) {
          strings.add(value.getValue());
        }
        return strings.toArray(new String[strings.size()]);
      }
      finally {
        StringSetSpinAllocator.dispose(strings);
      }
    }
    final String name = getName();
    if (name != null) {
      return new String[]{name};
    }
    return null;
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
  @Nullable
  @SuppressWarnings({"HardCodedStringLiteral"})
  public AntElement getFormatElement() {
    if (getTstampPropertyAttributeValue() != null) {
      for (final AntElement child : getChildren()) {
        if (child instanceof AntStructuredElement) {
          final AntStructuredElement se = (AntStructuredElement)child;
          if (AntFileImpl.FORMAT_TAG.equals(se.getSourceElement().getName())) {
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
    final String value = computeAttributeValue(sourceElement.getAttributeValue(AntFileImpl.FILE_ATTR));
    if (value != null) {
      return new File(value).getParent();
    }
    return value;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private String getTstampValue(final String propName) {
    final XmlTag se = getSourceElement();
    Date d = new Date();
    final XmlTag formatTag = se.findFirstSubTag(AntFileImpl.FORMAT_TAG);
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
    if (isTstamp()) {
      final XmlTag formatTag = getSourceElement().findFirstSubTag(AntFileImpl.FORMAT_TAG);
      if (formatTag != null) {
        final XmlAttribute propAttr = formatTag.getAttribute(AntFileImpl.PROPERTY, null);
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

  private boolean isTstamp() {
    return TSTAMP_TAG.equals(getSourceElement().getName());
  }
}

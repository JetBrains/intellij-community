// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jdom.Verifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * @deprecated use {@link com.intellij.util.xmlb.XmlSerializer} instead
 * @author mike
 */
@Deprecated
@SuppressWarnings("HardCodedStringLiteral")
public final class DefaultJDOMExternalizer {
  private static final Logger LOG = Logger.getInstance(DefaultJDOMExternalizer.class);

  private DefaultJDOMExternalizer() {
  }

  public interface JDOMFilter{
    boolean isAccept(@NotNull Field field);
  }

  public static void writeExternal(@NotNull Object data, @NotNull Element parentNode) throws WriteExternalException {
    writeExternal(data, parentNode, null);
  }

  private static final ConcurrentMap<Class, Map<String, Field>> ourFieldCache = ConcurrentFactoryMap.createMap(c -> {
    Map<String, Field> result = new LinkedHashMap<>();
    for (Field field : c.getFields()) {
      String name = field.getName();
      if (name.indexOf('$') >= 0 || result.containsKey(name)) continue;
      int modifiers = field.getModifiers();
      if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers) ||
          Modifier.isTransient(modifiers) || field.getAnnotation(Transient.class) != null) {
        continue;
      }

      field.setAccessible(true); // class might be non-public
      if (field.getDeclaringClass().getAnnotation(Transient.class) != null) {
        continue;
      }
      result.put(name, field);
    }
    return result;
  });

  public static void writeExternal(@NotNull Object data,
                                   @NotNull Element parentNode,
                                   @Nullable("null means all elements are accepted") JDOMFilter filter) throws WriteExternalException {
    for (Field field : ourFieldCache.get(data.getClass()).values()) {
      if (filter != null && !filter.isAccept(field)) {
        continue;
      }
      Class type = field.getType();
      String value = null;
      try {
        if (type.isPrimitive()) {
          if (type.equals(byte.class)) {
            value = Byte.toString(field.getByte(data));
          }
          else if (type.equals(short.class)) {
            value = Short.toString(field.getShort(data));
          }
          else if (type.equals(int.class)) {
            value = Integer.toString(field.getInt(data));
          }
          else if (type.equals(long.class)) {
            value = Long.toString(field.getLong(data));
          }
          else if (type.equals(float.class)) {
            value = Float.toString(field.getFloat(data));
          }
          else if (type.equals(double.class)) {
            value = Double.toString(field.getDouble(data));
          }
          else if (type.equals(char.class)) {
            value = String.valueOf(field.getChar(data));
          }
          else if (type.equals(boolean.class)) {
            value = Boolean.toString(field.getBoolean(data));
          }
          else {
            continue;
          }
        }
        else if (type.equals(String.class)) {
          value = filterXMLCharacters((String)field.get(data));
        }
        else if (type.isEnum()) {
          value = field.get(data).toString();
        }
        else if (type.equals(Color.class)) {
          Color color = (Color)field.get(data);
          if (color != null) {
            value = Integer.toString(color.getRGB() & 0xFFFFFF, 16);
          }
        }
        else if (ReflectionUtil.isAssignable(JDOMExternalizable.class, type)) {
          Element element = new Element("option");
          parentNode.addContent(element);
          element.setAttribute("name", field.getName());
          JDOMExternalizable domValue = (JDOMExternalizable)field.get(data);
          if (domValue != null) {
            Element valueElement = new Element("value");
            element.addContent(valueElement);
            domValue.writeExternal(valueElement);
          }
          continue;
        }
        else {
          LOG.debug("Wrong field type: " + type);
          continue;
        }
      }
      catch (IllegalAccessException e) {
        continue;
      }
      Element element = new Element("option");
      parentNode.addContent(element);
      element.setAttribute("name", field.getName());
      if (value != null) {
        element.setAttribute("value", value);
      }
    }
  }

  @Nullable
  static String filterXMLCharacters(@Nullable String value) {
    if (value == null) {
      return null;
    }

    StringBuilder builder = null;
    for (int i=0; i<value.length();i++) {
      char c = value.charAt(i);
      if (Verifier.isXMLCharacter(c)) {
        if (builder != null) {
          builder.append(c);
        }
      }
      else {
        if (builder == null) {
          builder = new StringBuilder(value.length()+5);
          builder.append(value, 0, i);
        }
      }
    }
    if (builder != null) {
      value = builder.toString();
    }
    return value;
  }

  public static void readExternal(@NotNull Object data, Element parentNode) throws InvalidDataException{
    if (parentNode == null) return;

    Map<String, Field> fields = ourFieldCache.get(data.getClass());

    for (Element e : parentNode.getChildren("option")) {
      String fieldName = e.getAttributeValue("name");
      if (fieldName == null) {
        throw new InvalidDataException();
      }
      try {
        Field field = fields.get(fieldName);
        if (field == null) {
          continue;
        }
        if (Modifier.isFinal(field.getModifiers())) {
          // read external contents of final field
          Object value = field.get(data);
          if (value instanceof JDOMExternalizable) {
            final List children = e.getChildren("value");
            for (Object child : children) {
              Element valueTag = (Element)child;
              ((JDOMExternalizable)value).readExternal(valueTag);
            }
          }
          continue;
        }
        String value = e.getAttributeValue("value");
        Class type = field.getType();
        if (type.isPrimitive()) {
          if (value != null) {
            if (type.equals(byte.class)) {
              try {
                field.setByte(data, Byte.parseByte(value));
              }
              catch (NumberFormatException ex) {
                throw new InvalidDataException();
              }
            }
            else if (type.equals(short.class)) {
              try {
                field.setShort(data, Short.parseShort(value));
              }
              catch (NumberFormatException ex) {
                throw new InvalidDataException();
              }
            }
            else if (type.equals(int.class)) {
              int i = toInt(value);
              field.setInt(data, i);
            }
            else if (type.equals(long.class)) {
              try {
                field.setLong(data, Long.parseLong(value));
              }
              catch (NumberFormatException ex) {
                throw new InvalidDataException();
              }
            }
            else if (type.equals(float.class)) {
              try {
                field.setFloat(data, Float.parseFloat(value));
              }
              catch (NumberFormatException ex) {
                throw new InvalidDataException();
              }
            }
            else if (type.equals(double.class)) {
              try {
                field.setDouble(data, Double.parseDouble(value));
              }
              catch (NumberFormatException ex) {
                throw new InvalidDataException();
              }
            }
            else if (type.equals(char.class)) {
              if (value.length() != 1) {
                throw new InvalidDataException();
              }
              field.setChar(data, value.charAt(0));
            }
            else if (type.equals(boolean.class)) {
              if (value.equals("true")) {
                field.setBoolean(data, true);
              }
              else if (value.equals("false")) {
                field.setBoolean(data, false);
              }
              else {
                throw new InvalidDataException();
              }
            }
            else {
              throw new InvalidDataException();
            }
          }
        }
        else if (type.isEnum()) {
          for (Object enumValue : type.getEnumConstants()) {
            if (enumValue.toString().equals(value)) {
              field.set(data, enumValue);
              break;
            }
          }
        }
        else if (type.equals(String.class)) {
          field.set(data, value);
        }
        else if (type.equals(Color.class)) {
          Color color = toColor(value);
          field.set(data, color);
        }
        else if (ReflectionUtil.isAssignable(JDOMExternalizable.class, type)) {
          final List<Element> children = e.getChildren("value");
          if (!children.isEmpty()) {
            // compatibility with Selena's serialization which writes an empty tag for a bean which has a default value
            JDOMExternalizable object = null;
            for (Element element : children) {
              object = (JDOMExternalizable)type.newInstance();
              object.readExternal(element);
            }

            field.set(data, object);
          }
        }
        else {
          throw new InvalidDataException("wrong type: " + type);
        }
      }
      catch (SecurityException | InstantiationException | IllegalAccessException ex) {
        throw new InvalidDataException(ex);
      }
    }
  }

  public static int toInt(@NotNull String value) throws InvalidDataException {
    int i;
    try {
      i = Integer.parseInt(value);
    }
    catch (NumberFormatException ex) {
      throw new InvalidDataException(value, ex);
    }
    return i;
  }

  public static Color toColor(@Nullable String value) throws InvalidDataException {
    Color color;
    if (value == null) {
      color = null;
    }
    else {
      try {
        int rgb = Integer.parseInt(value, 16);
        color = new Color(rgb);
      }
      catch (NumberFormatException ex) {
        LOG.debug("Wrong color value: " + value, ex);
        throw new InvalidDataException("Wrong color value: " + value, ex);
      }
    }
    return color;
  }

  @ApiStatus.Internal
  public static void clearFieldCache() {
    ourFieldCache.clear();
  }
}

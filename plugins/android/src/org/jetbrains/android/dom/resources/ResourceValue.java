/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.dom.resources;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class ResourceValue {
  private String myValue;
  private char myPrefix = 0;
  private String myPackage;
  private String myResourceType;
  private String myResourceName;

  private ResourceValue() {
  }

  public char getPrefix() {
    return myPrefix;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ResourceValue that = (ResourceValue)o;

    if (myPrefix != that.myPrefix) return false;
    if (myPackage != null ? !myPackage.equals(that.myPackage) : that.myPackage != null) return false;
    if (myResourceName != null ? !myResourceName.equals(that.myResourceName) : that.myResourceName != null) return false;
    if (myResourceType != null ? !myResourceType.equals(that.myResourceType) : that.myResourceType != null) return false;
    if (myValue != null ? !myValue.equals(that.myValue) : that.myValue != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myValue != null ? myValue.hashCode() : 0;
    result = 31 * result + (int)myPrefix;
    result = 31 * result + (myPackage != null ? myPackage.hashCode() : 0);
    result = 31 * result + (myResourceType != null ? myResourceType.hashCode() : 0);
    result = 31 * result + (myResourceName != null ? myResourceName.hashCode() : 0);
    return result;
  }

  @Nullable
  public static ResourceValue parse(String s, boolean withLiterals, boolean withPrefix) {
    if (s == null) {
      return null;
    }
    if (s.startsWith("@") || s.startsWith("?")) {
      return reference(s, true);
    }
    else if (!withPrefix) {
      ResourceValue value = reference(s, withPrefix);
      if (value != null) return value;
    }
    return withLiterals ? literal(s) : null;
  }

  public static ResourceValue literal(String value) {
    ResourceValue result = new ResourceValue();
    result.myValue = value;
    return result;
  }

  public static ResourceValue reference(String value) {
    return reference(value, true);
  }

  public static ResourceValue reference(String value, boolean withPrefix) {
    ResourceValue result = new ResourceValue();
    if (withPrefix) {
      assert value.length() > 0;
      result.myPrefix = value.charAt(0);
    }
    final int startIndex = withPrefix ? 1 : 0;
    int pos = value.indexOf('/');

    if (pos > 0) {
      String resType = value.substring(startIndex, pos);
      int colonIndex = resType.indexOf(':');
      if (colonIndex > 0) {
        result.myPackage = resType.substring(0, colonIndex);
        result.myResourceType = resType.substring(colonIndex + 1);
      }
      else {
        result.myResourceType = resType;
      }
      String suffix = value.substring(pos + 1);
      colonIndex = suffix.indexOf(':');
      if (colonIndex > 0) {
        String aPackage = suffix.substring(0, colonIndex);
        if (result.myPackage == null || result.myPackage.length() == 0 || aPackage.equals(result.myPackage)) {
          result.myPackage = aPackage;
          result.myResourceName = suffix.substring(colonIndex + 1);
        }
        return result;
      }
      result.myResourceName = suffix;
    }
    else {
      int colonIndex = value.indexOf(':');
      if (colonIndex > startIndex) {
        result.myPackage = value.substring(startIndex, colonIndex);
        result.myResourceName = value.substring(colonIndex + 1);
      }
      else {
        result.myResourceName = value.substring(startIndex);
      }
    }
    return result;
  }

  public static ResourceValue referenceTo(char prefix, @Nullable String resPackage, @Nullable String resourceType, String resourceName) {
    ResourceValue result = new ResourceValue();
    result.myPrefix = prefix;
    result.myPackage = resPackage;
    result.myResourceType = resourceType;
    result.myResourceName = resourceName;
    return result;
  }

  public boolean isReference() {
    return myValue == null;
  }

  @Nullable
  public String getValue() {
    return myValue;
  }

  @Nullable
  public String getResourceType() {
    return myResourceType;
  }

  @Nullable
  public String getResourceName() {
    return myResourceName;
  }

  @Nullable
  public String getPackage() {
    return myPackage;
  }

  @NotNull
  public String toString() {
    if (myValue != null) {
      return myValue;
    }
    final StringBuilder builder = new StringBuilder();
    if (myPrefix != 0) {
      builder.append(myPrefix);
    }
    if (myPackage != null) {
      builder.append(myPackage).append(":");
    }
    if (myResourceType != null) {
      builder.append(myResourceType).append("/");
    }
    builder.append(myResourceName);
    return builder.toString();
  }

  public void setResourceType(String resourceType) {
    myResourceType = resourceType;
  }
}

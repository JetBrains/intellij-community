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
package com.intellij.ui.mac.foundation;

import com.sun.jna.*;

/**
 * @author spleaner
 */
public class FoundationTypeMapper extends DefaultTypeMapper {

  public static final FoundationTypeMapper INSTANCE = new FoundationTypeMapper();

  private static final ToNativeConverter myToNativeConverter = new IDtoConverter();
  private static final FromNativeConverter myFromNativeConverter = new IDFromConverter();

  @Override
  public FromNativeConverter getFromNativeConverter(Class javaType) {
    if (javaType == ID.class) return myFromNativeConverter;
    return super.getFromNativeConverter(javaType);
  }

  @Override
  public ToNativeConverter getToNativeConverter(Class javaType) {
    if (javaType == ID.class) return myToNativeConverter;
    return  super.getToNativeConverter(javaType);
  }

  static class IDFromConverter implements FromNativeConverter {
    public Object fromNative(Object o, FromNativeContext fromNativeContext) {
      return new ID((Long) o);
    }

    public Class nativeType() {
      return Long.TYPE;
    }
  }

  static class IDtoConverter implements ToNativeConverter {
    public Object toNative(Object o, ToNativeContext toNativeContext) {
      if (o != null) {
        return ((ID)o).getAddress();
      } else {
        return new Long(0);
      }
    }

    public Class nativeType() {
      return Long.TYPE;
    }
  }
}

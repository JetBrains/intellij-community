/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.util.xmlb;

import org.jdom.Text;

import java.util.Date;

/**
 * @author Dmitry Avdeev
 */
public class DateBinding extends PrimitiveValueBinding {

  public DateBinding() {
    super(Date.class);
  }

  public Object serialize(Object o, Object context, SerializationFilter filter) {
    return new Text(Long.toString(((Date)o).getTime()));
  }

  @Override
  protected Object convertString(String value) {
    try {
      long l = Long.parseLong(value);
      return new Date(l);
    }
    catch (NumberFormatException e) {
      return new Date(0);
    }
  }
}

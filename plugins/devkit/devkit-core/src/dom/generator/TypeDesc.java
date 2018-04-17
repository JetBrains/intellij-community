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

/*
 * XSD/DTD Model generator tool
 *
 * By Gregory Shrago
 * 2002 - 2006
 */
package org.jetbrains.idea.devkit.dom.generator;

import java.util.Map;
import java.util.TreeMap;

/**
 * @author Konstantin Bulenkov
 */
public class TypeDesc {
  public enum TypeEnum {
    CLASS, ENUM, GROUP_INTERFACE
  }

  public TypeDesc(String xsName, String xsNamespace, String name, TypeEnum type) {
    this.xsName = xsName;
    this.xsNamespace = xsNamespace;
    this.name = name;
    this.type = type;
  }

  TypeEnum type;
  final String xsName;
  final String xsNamespace;
  final String name;
  final Map<String, FieldDesc> fdMap = new TreeMap<>();
  boolean duplicates;
  String documentation;
  TypeDesc[] supers;

  public String toString() {
    return (type == TypeEnum.ENUM ? "enum" : "type") + ": " + name + ";" + xsName + ";";
  }
}

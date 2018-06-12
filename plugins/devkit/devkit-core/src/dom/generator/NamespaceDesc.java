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
import java.util.HashMap;

/**
 * @author Konstantin Bulenkov
 */
public class NamespaceDesc {
  public NamespaceDesc(String name,
                       String pkgName,
                       String superClass,
                       String prefix,
                       String factoryClass,
                       String helperClass,
                       String imports,
                       String intfs) {
    this.name = name;
    this.pkgName = pkgName;
    this.superClass = superClass;
    this.prefix = prefix;
    this.factoryClass = factoryClass;
    this.helperClass = helperClass;
    this.imports = imports;
    this.intfs = intfs;
  }

  public NamespaceDesc(String name) {
    this(name, "generated", "java.lang.Object", "", null, null, null, null);
    skip = true;
  }


  public NamespaceDesc(String name, NamespaceDesc def) {
    this.name = name;
    this.pkgName = def.pkgName;
    this.superClass = def.superClass;
    this.prefix = def.prefix;
    this.factoryClass = def.factoryClass;
    this.helperClass = def.helperClass;
    this.imports = def.imports;
    this.intfs = def.intfs;
  }

  final Map<String, String> props = new HashMap<>();
  final String name;
  String pkgName;
  String superClass;
  String prefix;
  String factoryClass;
  String helperClass;
  String imports;
  String intfs;
  boolean skip;
  String[] pkgNames;
  String enumPkg;


  public String toString() {
    return "NS:"+name+" "+(skip?"skip":"")+pkgName;
  }
}

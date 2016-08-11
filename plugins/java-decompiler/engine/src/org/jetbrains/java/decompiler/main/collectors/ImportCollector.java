/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.main.collectors;

import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.TextBuffer;
import org.jetbrains.java.decompiler.struct.StructContext;

import java.util.*;
import java.util.Map.Entry;

public class ImportCollector {
  private static final String JAVA_LANG_PACKAGE = "java.lang";

  private final Map<String, String> mapSimpleNames = new HashMap<>();
  private final Set<String> setNotImportedNames = new HashSet<>();
  private final String currentPackageSlash;
  private final String currentPackagePoint;

  public ImportCollector(ClassNode root) {
    String clName = root.classStruct.qualifiedName;
    int index = clName.lastIndexOf('/');
    if (index >= 0) {
      String packageName = clName.substring(0, index);
      currentPackageSlash = packageName + '/';
      currentPackagePoint = packageName.replace('/', '.');
    }
    else {
      currentPackageSlash = "";
      currentPackagePoint = "";
    }
  }

  public String getShortName(String fullName) {
    return getShortName(fullName, true);
  }

  public String getShortName(String fullName, boolean imported) {
    ClassNode node = DecompilerContext.getClassProcessor().getMapRootClasses().get(fullName.replace('.', '/'));

    String result = null;
    if (node != null && node.classStruct.isOwn()) {
      result = node.simpleName;

      while (node.parent != null && node.type == ClassNode.CLASS_MEMBER) {
        result = node.parent.simpleName + '.' + result;
        node = node.parent;
      }

      if (node.type == ClassNode.CLASS_ROOT) {
        fullName = node.classStruct.qualifiedName;
        fullName = fullName.replace('/', '.');
      }
      else {
        return result;
      }
    }
    else {
      fullName = fullName.replace('$', '.');
    }

    String shortName = fullName;
    String packageName = "";

    int lastDot = fullName.lastIndexOf('.');
    if (lastDot >= 0) {
      shortName = fullName.substring(lastDot + 1);
      packageName = fullName.substring(0, lastDot);
    }

    StructContext context = DecompilerContext.getStructContext();

    // check for another class which could 'shadow' this one. Two cases:
    // 1) class with the same short name in the current package
    // 2) class with the same short name in the default package
    boolean existsDefaultClass =
      (context.getClass(currentPackageSlash + shortName) != null && !packageName.equals(currentPackagePoint)) || // current package
      (context.getClass(shortName) != null && !currentPackagePoint.isEmpty());  // default package

    if (existsDefaultClass ||
        (mapSimpleNames.containsKey(shortName) && !packageName.equals(mapSimpleNames.get(shortName)))) {
      //  don't return full name because if the class is a inner class, full name refers to the parent full name, not the child full name
      return result == null ? fullName : (packageName + "." + result);
    }
    else if (!mapSimpleNames.containsKey(shortName)) {
      mapSimpleNames.put(shortName, packageName);
      if (!imported) {
        setNotImportedNames.add(shortName);
      }
    }

    return result == null ? shortName : result;
  }

  public int writeImports(TextBuffer buffer) {
    int importLinesWritten = 0;

    List<String> imports = packImports();

    for (String s : imports) {
      buffer.append("import ");
      buffer.append(s);
      buffer.append(';');
      buffer.appendLineSeparator();

      importLinesWritten++;
    }

    return importLinesWritten;
  }

  private List<String> packImports() {
    List<Entry<String, String>> lst = new ArrayList<>(mapSimpleNames.entrySet());

    Collections.sort(lst, (par0, par1) -> {
      int res = par0.getValue().compareTo(par1.getValue());
      if (res == 0) {
        res = par0.getKey().compareTo(par1.getKey());
      }
      return res;
    });

    List<String> res = new ArrayList<>();
    for (Entry<String, String> ent : lst) {
      // exclude a current class or one of the nested ones, java.lang and empty packages
      if (!setNotImportedNames.contains(ent.getKey()) &&
          !JAVA_LANG_PACKAGE.equals(ent.getValue()) &&
          !ent.getValue().isEmpty()) {
        res.add(ent.getValue() + "." + ent.getKey());
      }
    }

    return res;
  }
}
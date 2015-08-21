/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import org.jetbrains.java.decompiler.main.ClassesProcessor;
import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.TextBuffer;
import org.jetbrains.java.decompiler.struct.StructContext;

import java.util.*;
import java.util.Map.Entry;


public class ImportCollector {

  private static final String JAVA_LANG_PACKAGE = "java.lang";

  private final Map<String, String> mapSimpleNames = new HashMap<String, String>();
  private final Set<String> setNotImportedNames = new HashSet<String>();
  private String currentPackageSlash = "";
  private String currentPackagePoint = "";

  public ImportCollector(ClassNode root) {

    String clname = root.classStruct.qualifiedName;
    int index = clname.lastIndexOf("/");
    if (index >= 0) {
      currentPackageSlash = clname.substring(0, index);
      currentPackagePoint = currentPackageSlash.replace('/', '.');
      currentPackageSlash += "/";
    }
  }

  public String getShortName(String fullname) {
    return getShortName(fullname, true);
  }

  public String getShortName(String fullname, boolean imported) {

    ClassesProcessor clproc = DecompilerContext.getClassProcessor();
    ClassNode node = clproc.getMapRootClasses().get(fullname.replace('.', '/'));

    String retname = null;

    if (node != null && node.classStruct.isOwn()) {

      retname = node.simpleName;

      while (node.parent != null && node.type == ClassNode.CLASS_MEMBER) {
        retname = node.parent.simpleName + "." + retname;
        node = node.parent;
      }

      if (node.type == ClassNode.CLASS_ROOT) {
        fullname = node.classStruct.qualifiedName;
        fullname = fullname.replace('/', '.');
      }
      else {
        return retname;
      }
    }
    else {
      fullname = fullname.replace('$', '.');
    }

    String nshort = fullname;
    String npackage = "";

    int lastpoint = fullname.lastIndexOf(".");

    if (lastpoint >= 0) {
      nshort = fullname.substring(lastpoint + 1);
      npackage = fullname.substring(0, lastpoint);
    }

    StructContext context = DecompilerContext.getStructContext();

    // check for another class which could 'shadow' this one. Two cases:
    // 1) class with the same short name in the current package
    // 2) class with the same short name in the default package
    boolean existsDefaultClass = (context.getClass(currentPackageSlash + nshort) != null
                                  && !npackage.equals(currentPackagePoint)) // current package
                                 || (context.getClass(nshort) != null 
                                  && !currentPackagePoint.isEmpty());  // default package

    if (existsDefaultClass ||
        (mapSimpleNames.containsKey(nshort) && !npackage.equals(mapSimpleNames.get(nshort)))) {
      //  don't return full name because if the class is a inner class, full name refers to the parent full name, not the child full name
      return retname == null ? fullname : (npackage + "." + retname);
    }
    else if (!mapSimpleNames.containsKey(nshort)) {
      mapSimpleNames.put(nshort, npackage);

      if (!imported) {
        setNotImportedNames.add(nshort);
      }
    }

    return retname == null ? nshort : retname;
  }

  public int writeImports(TextBuffer buffer) {
    int importlines_written = 0;

    List<String> imports = packImports();

    for (String s : imports) {
      buffer.append("import ");
      buffer.append(s);
      buffer.append(";");
      buffer.appendLineSeparator();

      importlines_written++;
    }

    return importlines_written;
  }

  private List<String> packImports() {
    List<Entry<String, String>> lst = new ArrayList<Entry<String, String>>(mapSimpleNames.entrySet());

    Collections.sort(lst, new Comparator<Entry<String, String>>() {
      public int compare(Entry<String, String> par0, Entry<String, String> par1) {
        int res = par0.getValue().compareTo(par1.getValue());
        if (res == 0) {
          res = par0.getKey().compareTo(par1.getKey());
        }
        return res;
      }
    });

    List<String> res = new ArrayList<String>();
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

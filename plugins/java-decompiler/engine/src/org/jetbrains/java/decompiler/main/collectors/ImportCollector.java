// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.main.collectors;

import org.jetbrains.java.decompiler.main.ClassesProcessor.ClassNode;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructContext;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructInnerClassesAttribute;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.*;
import java.util.stream.Collectors;

public class ImportCollector {
  private static final String JAVA_LANG_PACKAGE = "java.lang";

  private final Map<String, String> mapSimpleNames = new HashMap<>();
  private final Set<String> setNotImportedNames = new HashSet<>();
  // set of field names in this class and all its predecessors.
  private final Set<String> setFieldNames = new HashSet<>();
  private final Set<String> setInnerClassNames = new HashSet<>();
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

    Map<String, StructClass> classes = DecompilerContext.getStructContext().getClasses();
    LinkedList<String> queue = new LinkedList<>();
    Set<StructClass> processedClasses = new HashSet<>();
    StructClass currentClass = root.classStruct;
    while (currentClass != null) {
      processedClasses.add(currentClass);
      if (currentClass.superClass != null) {
        queue.add(currentClass.superClass.getString());
      }

      Collections.addAll(queue, currentClass.getInterfaceNames());

      // all field names for the current class ..
      for (StructField f : currentClass.getFields()) {
        setFieldNames.add(f.getName());
      }

      // .. all inner classes for the current class ..
      StructInnerClassesAttribute attribute = currentClass.getAttribute(StructGeneralAttribute.ATTRIBUTE_INNER_CLASSES);
      if (attribute != null) {
        for (StructInnerClassesAttribute.Entry entry : attribute.getEntries()) {
          if (entry.enclosingName != null && entry.enclosingName.equals(currentClass.qualifiedName)) {
            setInnerClassNames.add(entry.simpleName);
          }
        }
      }

      // .. and traverse through parent.
      do {
        currentClass = queue.isEmpty() ? null : classes.get(queue.removeFirst());

        if (currentClass != null && processedClasses.contains(currentClass)) {
          // Class already processed, skipping.

          // This may be sign of circularity in the class hierarchy but in most cases this mean that same interface
          // are listed as implemented several times in the class hierarchy.
          currentClass = null;
        }
      } while (currentClass == null && !queue.isEmpty());
    }
  }

  /**
   * Check whether the package-less name ClassName is shaded by variable in a context of
   * the decompiled class
   * @param classToName - pkg.name.ClassName - class to find the nested name for
   * @return ClassName if the name is not shaded by local field, pkg.name.ClassName otherwise
   */
  public String getNestedNameInClassContext(String classToName) {
    String nestedName = getNestedName(classToName);
    if (setFieldNames.contains(nestedName)) {
      return classToName;
    }
    else {
      return nestedName;
    }
  }

  public String getNestedName(String fullName) {
    return getNestedName(fullName, true);
  }

  public String getNestedName(String fullName, boolean imported) {
    ClassNode node = DecompilerContext.getClassProcessor().getMapRootClasses().get(fullName.replace('.', '/')); //todo[r.sh] anonymous classes?

    String nestedName;
    if (node != null && node.classStruct.isOwn()) {
      nestedName = node.simpleName;

      while (node.parent != null && node.type == ClassNode.CLASS_MEMBER) {
        //noinspection StringConcatenationInLoop
        nestedName = node.parent.simpleName + '.' + nestedName;
        node = node.parent;
      }

      if (node.type == ClassNode.CLASS_ROOT) {
        fullName = node.classStruct.qualifiedName;
        fullName = fullName.replace('/', '.');
      }
      else {
        return nestedName;
      }
    }
    else {
      fullName = fullName.replace('/', '.');
      int lastDot = fullName.lastIndexOf('.');
      if (lastDot != -1) {
        nestedName = fullName.substring(lastDot + 1).replace("$", ".");
      } else {
        nestedName = fullName;
      }
    }

    String outerShortName = fullName;
    String packageName = "";

    int lastDot = fullName.lastIndexOf('.');
    if (lastDot >= 0) {
      int firstNestedDot = nestedName.indexOf(".");
      if (firstNestedDot >= 0) {
        outerShortName = nestedName.substring(0, firstNestedDot);
      } else {
        outerShortName = nestedName;
      }
      packageName = fullName.substring(0, lastDot);
    }

    StructContext context = DecompilerContext.getStructContext();

    // check for another class which could 'shadow' this one. Three cases:
    // 1) class with the same short name in the current package
    // 2) class with the same short name in the default package
    // 3) inner class with the same short name in the current class, a super class, or an implemented interface
    boolean existsDefaultClass =
      (context.getClass(currentPackageSlash + outerShortName) != null && !packageName.equals(currentPackagePoint)) || // current package
      (context.getClass(outerShortName) != null && !currentPackagePoint.isEmpty()) || // default package
      setInnerClassNames.contains(outerShortName); // inner class

    if (existsDefaultClass || (mapSimpleNames.containsKey(outerShortName) && !packageName.equals(mapSimpleNames.get(outerShortName)))) {
      return packageName + "." + nestedName;
    }
    else if (!mapSimpleNames.containsKey(outerShortName)) {
      mapSimpleNames.put(outerShortName, packageName);
      if (!imported) {
        setNotImportedNames.add(outerShortName);
      }
    }

    return nestedName;
  }

  public void writeImports(TextBuffer buffer, boolean addSeparator) {
    List<String> imports = packImports();
    for (String line : imports) {
      buffer.append("import ").append(line).append(';').appendLineSeparator();
    }
    if (addSeparator && !imports.isEmpty()) {
      buffer.appendLineSeparator();
    }
  }

  private List<String> packImports() {
    return mapSimpleNames.entrySet().stream()
      .filter(ent ->
                // exclude the current class or one of the nested ones
                // empty, java.lang and the current packages
                !setNotImportedNames.contains(ent.getKey()) &&
                !ent.getValue().isEmpty() &&
                !JAVA_LANG_PACKAGE.equals(ent.getValue()) &&
                !ent.getValue().equals(currentPackagePoint)
      )
      .sorted(Map.Entry.<String, String>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
      .map(ent -> ent.getValue() + "." + ent.getKey())
      .collect(Collectors.toList());
  }
}
